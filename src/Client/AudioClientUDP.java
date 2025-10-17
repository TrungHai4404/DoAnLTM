package Client;

import javax.sound.sampled.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class AudioClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5001;
    private volatile boolean running = true;
    private volatile boolean micEnabled = true;

    private final int PACKET_SIZE = 1024;
    private final int SAMPLE_RATE = 16000;
    private final int SAMPLE_SIZE = 16;
    private final int CHANNELS = 1;

    private final int MAX_BUFFER = 50;
    private Map<Integer, byte[]> jitterBuffer = new ConcurrentHashMap<>();
    private volatile int expectedSeq = 0;

    public AudioClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
        serverAddr = InetAddress.getByName(serverIP);
    }

    /** Start sending mic audio */
    public void startSending() {
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
                mic.open(format);
                mic.start();

                byte[] buffer = new byte[PACKET_SIZE];
                int seq = 0;
                System.out.println("🎤 Sending audio...");

                while (running) {
                    if (micEnabled) {
                        int bytesRead = mic.read(buffer, 0, buffer.length);
                        byte[] data = new byte[bytesRead + 4];
                        data[0] = (byte)((seq >> 24) & 0xFF);
                        data[1] = (byte)((seq >> 16) & 0xFF);
                        data[2] = (byte)((seq >> 8) & 0xFF);
                        data[3] = (byte)(seq & 0xFF);
                        System.arraycopy(buffer, 0, data, 4, bytesRead);

                        DatagramPacket pkt = new DatagramPacket(data, data.length, serverAddr, port);
                        socket.send(pkt);
                        seq++;
                    } else {
                        Thread.sleep(10);
                    }
                }
                mic.close();
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }).start();
    }

    /** Start receiving audio */
    public void startReceiving() {
        // Thread nhận UDP
        new Thread(() -> {
            try {
                byte[] buffer = new byte[PACKET_SIZE + 4];
                while (running) {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);

                    int seq = ((buffer[0]&0xFF)<<24)|((buffer[1]&0xFF)<<16)|((buffer[2]&0xFF)<<8)|(buffer[3]&0xFF);
                    byte[] data = new byte[pkt.getLength()-4];
                    System.arraycopy(buffer, 4, data, 0, data.length);

                    // Thêm vào jitter buffer
                    jitterBuffer.put(seq, data);
                    // Giữ buffer max 50 gói
                    if (jitterBuffer.size() > MAX_BUFFER) {
                        jitterBuffer.keySet().removeIf(k -> k < expectedSeq);
                    }
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }).start();

        // Thread phát audio
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(format);
                speakers.start();

                while (running) {
                    byte[] data = jitterBuffer.remove(expectedSeq);
                    if (data != null) {
                        speakers.write(data, 0, data.length);
                    }
                    expectedSeq++;
                    Thread.sleep(5);
                }
                speakers.close();
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }).start();
    }

    public void toggleMic() {
        micEnabled = !micEnabled;
        System.out.println(micEnabled ? "🎤 Mic ON" : "🔇 Mic OFF");
    }

    public void stop() {
        running = false;
        socket.close();
    }

    private AudioFormat getAudioFormat() {
        return new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, true, false);
    }
}
