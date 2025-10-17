package Client;

import javax.sound.sampled.*;
import java.net.*;
import java.nio.ByteBuffer;

public class AudioClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5001;
    private volatile boolean running = true;
    private volatile boolean micEnabled = true;
    private int sendSequence = 0;
    private int lastReceivedSeq = -1;

    private final int PACKET_SIZE = 1024; // gói nhỏ

    public AudioClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
        socket.setReceiveBufferSize(65536);
        socket.setSendBufferSize(65536);
        serverAddr = InetAddress.getByName(serverIP);
    }

    public void startSending() {
        new Thread(() -> {
            TargetDataLine mic = null;
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                mic = (TargetDataLine) AudioSystem.getLine(info);
                mic.open(format);
                mic.start();

                byte[] audioBuffer = new byte[PACKET_SIZE - 4]; // 4 bytes sequence
                ByteBuffer sendBuf = ByteBuffer.allocate(PACKET_SIZE);

                while (running) {
                    if (micEnabled) {
                        int bytesRead = mic.read(audioBuffer, 0, audioBuffer.length);
                        if (bytesRead > 0) {
                            sendBuf.clear();
                            sendBuf.putInt(sendSequence++);   // 4 byte seq
                            sendBuf.put(audioBuffer, 0, bytesRead);
                            DatagramPacket pkt = new DatagramPacket(sendBuf.array(), bytesRead + 4, serverAddr, port);
                            socket.send(pkt);
                        }
                    } else {
                        Thread.sleep(50);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mic != null) mic.close();
            }
        }).start();
    }

    public void startReceiving() {
        new Thread(() -> {
            SourceDataLine speakers = null;
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(format);
                speakers.start();

                byte[] recvBuffer = new byte[PACKET_SIZE];

                while (running) {
                    try {
                        DatagramPacket pkt = new DatagramPacket(recvBuffer, recvBuffer.length);
                        socket.receive(pkt);

                        ByteBuffer bb = ByteBuffer.wrap(pkt.getData(), 0, pkt.getLength());
                        int seq = bb.getInt();
                        byte[] audio = new byte[pkt.getLength() - 4];
                        bb.get(audio);

                        // Nếu sequence <= lastReceivedSeq → bỏ gói cũ
                        if (seq <= lastReceivedSeq) continue;
                        lastReceivedSeq = seq;

                        speakers.write(audio, 0, audio.length);
                    } catch (SocketException se) {
                        if (!running) break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (speakers != null) speakers.close();
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
        return new AudioFormat(16000.0F, 16, 1, true, false);
    }
}
