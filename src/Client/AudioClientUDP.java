package Client;

import javax.sound.sampled.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5001;
    private AtomicBoolean running = new AtomicBoolean(true);
    private AtomicBoolean micEnabled = new AtomicBoolean(true);
    private TargetDataLine micLine;
    private SourceDataLine speakerLine;
    private static final int PACKET_SIZE = 4096;
    private int sendSequence = 0;

    public AudioClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
        socket.setReceiveBufferSize(1024 * 1024);
        socket.setSendBufferSize(1024 * 1024);
        serverAddr = InetAddress.getByName(serverIP);
    }

    /** Start gửi âm thanh */
    public void startSending() {
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                micLine = (TargetDataLine) AudioSystem.getLine(info);
                micLine.open(format);
                micLine.start();

                byte[] audioBuffer = new byte[PACKET_SIZE - 4];
                ByteBuffer sendBuf = ByteBuffer.allocate(PACKET_SIZE);

                while (running.get()) {
                    int bytesRead = 0;
                    if (micEnabled.get()) {
                        bytesRead = micLine.read(audioBuffer, 0, audioBuffer.length);
                    }

                    if (bytesRead > 0) {
                        sendBuf.clear();
                        sendBuf.putInt(sendSequence++);
                        sendBuf.put(audioBuffer, 0, bytesRead);
                        DatagramPacket pkt = new DatagramPacket(sendBuf.array(), bytesRead + 4, serverAddr, port);
                        socket.send(pkt);
                    } else {
                        Thread.sleep(10);
                    }
                }
            } catch (Exception e) {
                if (running.get()) e.printStackTrace();
            }
        }, "Audio-Sender").start();
    }

    /** Start nhận âm thanh */
    public void startReceiving() {
        new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                speakerLine.open(format);
                speakerLine.start();

                byte[] buffer = new byte[PACKET_SIZE];

                while (running.get()) {
                    DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pkt);

                    ByteBuffer wrap = ByteBuffer.wrap(pkt.getData(), 0, pkt.getLength());
                    int seq = wrap.getInt(); // sequence number, dùng cho nâng cấp về sau
                    byte[] audio = new byte[pkt.getLength() - 4];
                    wrap.get(audio);

                    speakerLine.write(audio, 0, audio.length);
                }
            } catch (Exception e) {
                if (running.get()) e.printStackTrace();
            }
        }, "Audio-Receiver").start();
    }

    public void stop() {
        running.set(false);
        if (micLine != null) micLine.close();
        if (speakerLine != null) speakerLine.close();
        socket.close();
    }

    public void toggleMic() {
        micEnabled.set(!micEnabled.get());
        System.out.println(micEnabled.get() ? "🎤 Micro bật" : "🔇 Micro tắt");
    }
    public boolean isMicEnabled(){
        return micEnabled.get();
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
