package Client;

import javax.sound.sampled.*;
import java.net.*;

public class AudioClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5001;
    private boolean running = true;
    private boolean micEnabled = true;

    public AudioClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
        serverAddr = InetAddress.getByName(serverIP);
    }

    // Gửi âm thanh (micro)
    public void startSending() {
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
                mic.open(format);
                mic.start();

                byte[] buffer = new byte[4096];
                System.out.println("🎤 Bắt đầu gửi âm thanh...");

                while (running) {
                    if (micEnabled) {
                        int bytesRead = mic.read(buffer, 0, buffer.length);
                        DatagramPacket pkt = new DatagramPacket(buffer, bytesRead, serverAddr, port);
                        socket.send(pkt);
                    }
                }
                mic.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Nhận âm thanh và phát ra loa
    public void startReceiving() {
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(format);
                speakers.start();

                byte[] buffer = new byte[4096];
                System.out.println("🔊 Đang nhận và phát âm thanh...");

                while (running) {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);
                    speakers.write(pkt.getData(), 0, pkt.getLength());
                }
                speakers.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stop() {
        running = false;
        socket.close();
    }

    public void toggleMic() {
        micEnabled = !micEnabled;
        System.out.println(micEnabled ? "🎤 Micro bật" : "🔇 Micro tắt");
    }

    public boolean isMicEnabled() {
        return micEnabled;
    }

    private AudioFormat getAudioFormat() {
        float sampleRate = 16000.0F;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}
