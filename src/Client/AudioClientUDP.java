package Client;

import java.io.IOException;
import javax.sound.sampled.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.SwingUtilities;

public class AudioClientUDP {
    private final int port = 5001;
    private final int BUFFER_SIZE = 512; // Kích thước buffer nhỏ hơn để giảm độ trễ
    
    private static final byte[] HEARTBEAT_DATA = "HBEAT".getBytes();
    private static final int HEARTBEAT_INTERVAL = 3000; // 3 giây gửi ping
    private static final int HEARTBEAT_TIMEOUT = 9000;
    private volatile boolean disconnectedHandled = false;
    private String roomCode;
    private String clientID;

    public interface ConnectionListener {
        void onServerDisconnected(String type);
    }
    
    private ConnectionListener listener;
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    private DatagramSocket socket;
    private InetAddress serverAddr;

    // 💡 SỬA LỖI: Chuyển mic và speakers thành biến của lớp để có thể truy cập và đóng chúng
    private TargetDataLine mic;
    private SourceDataLine speakers;

    private volatile boolean running = true;
    private volatile boolean micEnabled = true;

    // 💡 TỐI ƯU: Jitter buffer
    private final ConcurrentLinkedQueue<byte[]> jitterBuffer = new ConcurrentLinkedQueue<>();
    private final int JITTER_BUFFER_MIN_SIZE = 3; // Bắt đầu phát khi có ít nhất 3 gói

    private volatile long lastResponseTime = System.currentTimeMillis();

    public AudioClientUDP(String serverIP,String roomCode, String clientID) throws Exception {
        this.roomCode = roomCode;
        this.clientID = clientID;
        socket = new DatagramSocket();            // cổng ngẫu nhiên
        socket.setSoTimeout(3000);                // để vòng nhận thoát ra kiểm tra timeout
        socket.setReceiveBufferSize(1 << 20);
        socket.setSendBufferSize(1 << 20);
        serverAddr = InetAddress.getByName(serverIP);
    }

    public boolean toggleMic() {
        micEnabled = !micEnabled;
        System.out.println(micEnabled ? " Micro on" : "🔇 Micro off");
        return micEnabled;
    }
    public void stop() {
        running = false; // Tín hiệu cho các luồng dừng lại

        // 💡 SỬA LỖI: Đóng và giải phóng tài nguyên mic và loa
        if (mic != null && mic.isOpen()) {
            mic.stop();
            mic.close();
            System.out.println("Mic release.");
        }
        if (speakers != null && speakers.isOpen()) {
            speakers.stop();
            speakers.close();
            System.out.println("Speaker release");
        }

        // Đóng socket sau cùng để ngắt các luồng đang chờ
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
    
    public void start() {
        try {
            initAudioLines();
            startSending();
            startReceiving();
            startPlaying(); // 💡 SỬA LỖI: Bắt đầu luồng phát âm thanh riêng biệt
            startHeartbeatSender();
            startHeartbeatMonitor();
        } catch (LineUnavailableException e) {
            System.err.println("Khong the truy cap Micro va Loa");
            stop(); // Dọn dẹp nếu không khởi tạo được
        }
    }
    
    private void initAudioLines() throws LineUnavailableException {
        AudioFormat format = getAudioFormat();
        // Khởi tạo Mic
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
        mic = (TargetDataLine) AudioSystem.getLine(micInfo);
        mic.open(format, BUFFER_SIZE * 2);
        mic.start();

        // Khởi tạo Loa
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
        speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speakers.open(format, BUFFER_SIZE * 4); // Buffer loa lớn hơn một chút
        speakers.start();
    }

    private void startSending() {
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (running) {
                try {
                    if (!micEnabled) {
                        Thread.sleep(40);
                        continue;
                    }else if (micEnabled) {
                        int bytesRead = mic.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            sendAudio(Arrays.copyOf(buffer, bytesRead));
                        }
                    } else {
                        // 💡 SỬA LỖI: Gửi heartbeat khi mic tắt
                        DatagramPacket heartbeatPkt = new DatagramPacket(HEARTBEAT_DATA, HEARTBEAT_DATA.length, serverAddr, port);
                        socket.send(heartbeatPkt);
                        Thread.sleep(2000); // Gửi 2 giây một lần
                    }
                } catch (Exception e) {
                    if (running) System.err.println("Lỗi gửi audio: " + e.getMessage());
                }
            }
            System.out.println("Luồng gửi audio đã dừng.");
        }, "Audio-Sender").start();
    }
    private void sendAudio(byte[] audioData) {
        try {
            byte[] roomBytes = new byte[36];
            byte[] idBytes = new byte[36];

            System.arraycopy(roomCode.getBytes(), 0, roomBytes, 0, Math.min(roomCode.length(), 36));
            System.arraycopy(clientID.getBytes(), 0, idBytes, 0, Math.min(clientID.length(), 36));

            byte[] combined = new byte[72 + audioData.length];
            System.arraycopy(roomBytes, 0, combined, 0, 36);
            System.arraycopy(idBytes, 0, combined, 36, 36);
            System.arraycopy(audioData, 0, combined, 72, audioData.length);

            DatagramPacket pkt = new DatagramPacket(combined, combined.length, serverAddr, port);
            socket.send(pkt);
        } catch (Exception e) {
            if (running) System.err.println("Lỗi gửi audio: " + e.getMessage());
        }
    }

    private void startReceiving() {
    new Thread(() -> {
        byte[] buffer = new byte[BUFFER_SIZE * 2];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                socket.receive(pkt);
                lastResponseTime = System.currentTimeMillis();
                
                byte[] receivedData = Arrays.copyOf(pkt.getData(), pkt.getLength());
                if (Arrays.equals(receivedData, HEARTBEAT_DATA)) {
                    continue;
                }
                jitterBuffer.offer(receivedData);
            } catch (SocketTimeoutException e) {
                // timeout → bỏ qua
            } catch (SocketException e) {
                if (!running)
                    break;
                notifyDisconnect("AUDIO", e);
                break;
            } catch (IOException e) {
                if (running) notifyDisconnect("AUDIO", e);
                break;
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }
        System.out.println("Luồng nhận audio đã dừng.");
    }, "Audio-Receiver").start();
}

    // 💡 SỬA LỖI: Luồng riêng để phát âm thanh từ Jitter Buffer
    private void startPlaying() {
        new Thread(() -> {
            while (running) {
                try {
                    // Chỉ phát khi buffer có đủ dữ liệu để đảm bảo mượt mà
                    if (jitterBuffer.size() >= JITTER_BUFFER_MIN_SIZE) {
                        byte[] data = jitterBuffer.poll();
                        if (data != null) {
                            speakers.write(data, 0, data.length);
                        }
                    } else {
                        // Nếu buffer rỗng, đợi một chút
                        Thread.sleep(10); 
                    }
                } catch (Exception e) {
                    if (running) System.err.println("Lỗi phát audio: " + e.getMessage());
                }
            }
             System.out.println("Luồng phát audio đã dừng.");
        }, "Audio-Player").start();
    }
    /** GỬI HEARTBEAT ĐỊNH KỲ (độc lập audio) */
    private void startHeartbeatSender() {
        new Thread(() -> {
            while (running) {
                try {
                    DatagramPacket hb = new DatagramPacket(HEARTBEAT_DATA, HEARTBEAT_DATA.length, serverAddr, port);
                    socket.send(hb);
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (Exception e) {
                    if (running) System.err.println("⚠️ Lỗi gửi heartbeat: " + e.getMessage());
                }
            }
        }, "Audio-HB-Send").start();
    }
    /** GIÁM SÁT: quá 9s không nhận → mất kết nối */
    private void startHeartbeatMonitor() {
        new Thread(() -> {
            while (running) {
                try {
                    if (System.currentTimeMillis() - lastResponseTime > HEARTBEAT_TIMEOUT) {
                        notifyDisconnect("AUDIO", null);
                        break;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {}
            }
        }, "Audio-HB-Mon").start();
    }
    private void notifyDisconnect(String type, Exception e) {
        if (disconnectedHandled) return; // tránh gọi nhiều lần
        disconnectedHandled = true;

        System.err.println("🔌 Mất kết nối tới " + type + " server"
                + (e != null ? ": " + e.getMessage() : ""));

        running = false; // dừng tất cả các vòng while
        //stop(); // đóng mic, speaker, socket,...

        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onServerDisconnected(type));
        }
    }
    private AudioFormat getAudioFormat() {
        float sampleRate = 16000.0F; // 💡 TỐI ƯU: Dùng 16kHz, tốt hơn cho voice chat và tương thích rộng rãi
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
    
}