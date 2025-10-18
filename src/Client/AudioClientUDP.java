package Client;

import javax.sound.sampled.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioClientUDP {
    private final int port = 5001;
    private final int BUFFER_SIZE = 512; // Kích thước buffer nhỏ hơn để giảm độ trễ
    private static final byte[] HEARTBEAT_DATA = "HBEAT".getBytes();

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

    public AudioClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
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
                        Thread.sleep(200);
                        continue;
                    }else if (micEnabled) {
                        int bytesRead = mic.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            DatagramPacket pkt = new DatagramPacket(buffer, 0, bytesRead, serverAddr, port);
                            socket.send(pkt);
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

    private void startReceiving() {
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE * 2]; // Buffer nhận lớn hơn một chút
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);
                    
                    // Chỉ thêm vào buffer nếu là dữ liệu âm thanh, không phải heartbeat
                    byte[] receivedData = Arrays.copyOf(pkt.getData(), pkt.getLength());
                    if (!Arrays.equals(receivedData, HEARTBEAT_DATA)) {
                        jitterBuffer.offer(receivedData);
                    }
                } catch (Exception e) {
                    if (running) System.err.println("Lỗi nhận audio: " + e.getMessage());
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

    private AudioFormat getAudioFormat() {
        float sampleRate = 16000.0F; // 💡 TỐI ƯU: Dùng 16kHz, tốt hơn cho voice chat và tương thích rộng rãi
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
    

}