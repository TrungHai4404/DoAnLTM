package Client;

import javax.sound.sampled.*;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5001;
    private volatile boolean running = true;
    private volatile boolean micEnabled = true;
    private final int BUFFER_SIZE = 512;

    // Jitter buffer để giảm drop
    private ConcurrentLinkedQueue<byte[]> jitterBuffer = new ConcurrentLinkedQueue<>();

    public AudioClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
        serverAddr = InetAddress.getByName(serverIP);
    }

    /** Bật/tắt mic */
    public void toggleMic() {
        micEnabled = !micEnabled;
        System.out.println(micEnabled ? "🎤 Micro bật" : "🔇 Micro tắt");
    }

    /** Ngừng gửi/nhận audio */
    public void stop() {
        running = false;
        socket.close();
    }

    /** Bắt đầu gửi âm thanh */
    public void startSending() {
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
                mic.open(format);
                mic.start();

                byte[] buffer = new byte[BUFFER_SIZE];

                while (running) {
                    if (micEnabled) {
                        int bytesRead = mic.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            DatagramPacket pkt = new DatagramPacket(buffer, bytesRead, serverAddr, port);
                            socket.send(pkt);
                        }
                    } else {
                        Thread.sleep(2); // Mic tắt -> sleep ngắn
                    }
                }
                mic.close();
            } catch (Exception e) {
                if(running) e.printStackTrace();
            }
        }).start();
    }

    /** Bắt đầu nhận âm thanh */
    public void startReceiving() {
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(format);
                speakers.start();

                byte[] buffer = new byte[BUFFER_SIZE];

                while (running) {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);

                    byte[] data = new byte[pkt.getLength()];
                    System.arraycopy(pkt.getData(), 0, data, 0, pkt.getLength());
                    jitterBuffer.offer(data);

                    // Phát ngay khi có gói
                    byte[] playData = jitterBuffer.poll();
                    if (playData != null) {
                        speakers.write(playData, 0, playData.length);
                    }
                }
                speakers.drain();
                speakers.close();
            } catch (Exception e) {
                if(running) e.printStackTrace();
            }
        }).start();
    }

    /** Audio format */
    private AudioFormat getAudioFormat() {
        float sampleRate = 44100.0F; // Chuẩn 44.1kHz
        int sampleSizeInBits = 16;
        int channels = 1; // mono
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}
