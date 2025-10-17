package Client;

import javax.sound.sampled.*;
import java.net.*;

public class AudioClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5001;
    private boolean running = true;
    private boolean micEnabled = true;
    private static final byte[] HEARTBEAT_DATA = "HBEAT".getBytes();
    private TargetDataLine mic;
    private SourceDataLine speakers;
    private AudioFormat format;

    public AudioClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
        serverAddr = InetAddress.getByName(serverIP);
        format = getAudioFormat();
    }

    // 🔊 Bắt đầu gửi âm thanh
    public void startSending() {
        new Thread(() -> {
            try {
                // ... khởi tạo mic ...
                byte[] buffer = new byte[4096];
                System.out.println("🎤 Bắt đầu gửi âm thanh...");

                while (running) {
                    if (micEnabled) {
                        int bytesRead = mic.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            DatagramPacket pkt = new DatagramPacket(buffer, bytesRead, serverAddr, port);
                            socket.send(pkt);
                        }
                    } else {
                        // 💡 BƯỚC 2: Khi mic tắt, gửi heartbeat 2 giây một lần
                        DatagramPacket heartbeatPkt = new DatagramPacket(HEARTBEAT_DATA, HEARTBEAT_DATA.length, serverAddr, port);
                        socket.send(heartbeatPkt);
                        Thread.sleep(2000); // Gửi heartbeat và nghỉ 2 giây
                    }
                }
                // ... dọn dẹp mic ...
            } catch (Exception e) {
                if (running) { // Chỉ in lỗi nếu client vẫn đang chạy
                    e.printStackTrace();
                }
            }
        }, "Mic-Sender-Thread").start();
    }

    // 🔈 Nhận và phát âm thanh
    public void startReceiving() {
        new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                speakers = (SourceDataLine) AudioSystem.getLine(info);

                // 💡 TỐI ƯU 2: Mở loa với một bộ đệm lớn hơn
                // Kích thước buffer = sampleRate * channels * (bytes per sample) * (buffer duration in seconds)
                // Ví dụ: 16000 * 1 * 2 * 0.1 = 3200 bytes cho 100ms buffer
                int bufferSize = (int) format.getSampleRate() * format.getFrameSize() * 2;
                speakers.open(format, bufferSize); 

                speakers.start();

                byte[] buffer = new byte[4096];
                System.out.println("🔊 Đang nhận và phát âm thanh với buffer " + bufferSize + " bytes...");

                while (running) {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);
                    speakers.write(pkt.getData(), 0, pkt.getLength());
                }

                speakers.drain();
                speakers.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Speaker-Receiver-Thread").start();
    }

    // 📴 Dừng toàn bộ
    public void stop() {
        running = false;
        socket.close();
        if (mic != null && mic.isOpen()) mic.close();
        if (speakers != null && speakers.isOpen()) speakers.close();
    }

    // 🎤 Bật/Tắt micro
    public boolean toggleMic() { // Sửa từ void thành boolean
        micEnabled = !micEnabled;
        if (micEnabled) {
            System.out.println("🎤 Mic đã được bật.");
        } else {
            System.out.println("🔇 Mic đã được tắt.");
        }
        return micEnabled; // Trả về trạng thái mới của mic
    }

    private AudioFormat getAudioFormat() {
        // Format an toàn, tương thích cao
        float sampleRate = 16000.0F;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}
