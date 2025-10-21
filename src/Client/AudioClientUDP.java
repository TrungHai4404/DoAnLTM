package Client;

import java.io.IOException;
import javax.sound.sampled.*;
import java.net.*;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class AudioClientUDP {
    private final int port = 5001;
    private final int BUFFER_SIZE = 512; // Kích thước buffer nhỏ hơn để giảm độ trễ
    
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
    public String getClientID() { // Thêm hàm này để debug
        return this.clientID;
    }
    private DatagramSocket socket;
    private InetAddress serverAddr;

    // 💡 SỬA LỖI: Chuyển mic và speakers thành biến của lớp để có thể truy cập và đóng chúng
    private TargetDataLine mic;
    private SourceDataLine speakers;

    private volatile boolean running = true;
    private volatile boolean micEnabled = false;

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
    // Yêu cầu bật mic
    private boolean enableMic() {
        if (GlobalMicController.getInstance().requestMicAccess(this)) {
            // Được cấp quyền, bắt đầu mở và đọc micro
            try {
                if (mic == null || !mic.isOpen()) {
                    initMic(); // Hàm riêng để khởi tạo chỉ mic
                }
                micEnabled = true;
                System.out.println(clientID + ": Micro is now ON.");
                return true;
            } catch (LineUnavailableException e) {
                System.err.println("Lỗi: Không thể mở mic dù đã được cấp quyền.");
                GlobalMicController.getInstance().releaseMicAccess(this); // Trả lại quyền
                return false;
            }
        } else {
            // Không được cấp quyền, thông báo cho người dùng
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, 
                    "Micro đang được sử dụng ở một phòng khác!", 
                    "Xung đột Micro", 
                    JOptionPane.WARNING_MESSAGE);
            });
            micEnabled = false; // Đảm bảo trạng thái là tắt
            return false;
        }
    }
    // Giải phóng mic
    private void disableMic() {
        GlobalMicController.getInstance().releaseMicAccess(this);
        if (mic != null && mic.isOpen()) {
            mic.stop();
            mic.close();
            System.out.println(clientID + ": Mic resource released.");
        }
        micEnabled = false;
        System.out.println(clientID + ": Micro is now OFF.");
    }
    
    public boolean toggleMic() {
        if (micEnabled) { // Nếu đang bật -> thì TẮT
            disableMic();
            System.out.println("Mic Da Tat");
            return false;
        } else { // Nếu đang tắt -> thì cố gắng BẬT
            // enableMic() sẽ tự xử lý việc xin quyền và trả về true/false
            System.out.println("Mic Da Bat");
            return enableMic();
        }
    }
    // Tách hàm khởi tạo mic để gọi khi cần
    private void initMic() throws LineUnavailableException {
        AudioFormat format = getAudioFormat();
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(micInfo)) {
            throw new LineUnavailableException("Mic line not supported");
        }
        mic = (TargetDataLine) AudioSystem.getLine(micInfo);
        mic.open(format, BUFFER_SIZE * 2);
        mic.start();
    }
    public void stop() {
        running = false; // Tín hiệu cho các luồng dừng lại
        disableMic(); // đóng phòng là giải phóng mic
        
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
            this.lastResponseTime = System.currentTimeMillis();
            initAudioLines();
            startSending();
            startReceiving();
            startPlaying(); // 💡 SỬA LỖI: Bắt đầu luồng phát âm thanh riêng biệt
            startHeartbeatMonitor();
        } catch (LineUnavailableException e) {
            System.err.println("Khong the truy cap Micro va Loa");
            stop(); // Dọn dẹp nếu không khởi tạo được
        }
    }
    
    private void initAudioLines() throws LineUnavailableException {
        AudioFormat format = getAudioFormat();
        // Khởi tạo Loa
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
        speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speakers.open(format, BUFFER_SIZE * 4); // Buffer loa lớn hơn một chút
        speakers.start();
        
        //Mặc định tắt mic khi vào phòng
        micEnabled = false;
    }

    private void startSending() {
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (running) {
                try {
                    if (micEnabled && mic != null && mic.isOpen()) {
                        int bytesRead = mic.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            sendAudio(Arrays.copyOf(buffer, bytesRead));
                        }
                    } else {
                        sendAudio(new byte[0]);
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

            // Sửa lỗi: Luôn dùng UTF-8
            byte[] roomCodeData = roomCode.getBytes(StandardCharsets.UTF_8);
            byte[] clientIDData = clientID.getBytes(StandardCharsets.UTF_8);

            System.arraycopy(roomCodeData, 0, roomBytes, 0, Math.min(roomCodeData.length, 36));
            System.arraycopy(clientIDData, 0, idBytes, 0, Math.min(clientIDData.length, 36));

            // Cấu trúc 72-byte header + payload
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
            byte[] buffer = new byte[BUFFER_SIZE * 4];
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);
                    lastResponseTime = System.currentTimeMillis();

                    byte[] receivedData = Arrays.copyOf(pkt.getData(), pkt.getLength());
                    
                    if (receivedData.length < 72) continue;
                    // 🧩 Tách header
                    String roomCodeFrame = new String(Arrays.copyOfRange(receivedData, 0, 36)).trim();
                    String senderID = new String(Arrays.copyOfRange(receivedData, 36, 72)).trim();
                    byte[] audioData = Arrays.copyOfRange(receivedData, 72, receivedData.length);
                    if (!roomCodeFrame.equals(this.roomCode)) continue;
                    if (!roomCodeFrame.equals(this.roomCode)) continue;
                    if (audioData.length > 0) {
                        jitterBuffer.offer(audioData);
                    }
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