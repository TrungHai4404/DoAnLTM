package Client;

import java.io.IOException;
import javax.sound.sampled.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class AudioClientUDP {
    private final int port = 5001;
    private final int BUFFER_SIZE = 512;

    private static final byte[] HEARTBEAT_DATA = "HBEAT".getBytes();
    private static final int HEARTBEAT_INTERVAL = 3000;
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

    public String getClientID() {
        return this.clientID;
    }

    private DatagramSocket socket;
    private InetAddress serverAddr;

    private TargetDataLine mic;
    private SourceDataLine speakers;

    private volatile boolean running = true;
    private volatile boolean micEnabled = false;

    private final ConcurrentLinkedQueue<byte[]> jitterBuffer = new ConcurrentLinkedQueue<>();
    private final int JITTER_BUFFER_MIN_SIZE = 3;

    private volatile long lastResponseTime = System.currentTimeMillis();

    public AudioClientUDP(String serverIP, String roomCode, String clientID) throws Exception {
        this.roomCode = roomCode;
        this.clientID = clientID;
        socket = new DatagramSocket();
        socket.setSoTimeout(3000);
        socket.setReceiveBufferSize(1 << 20);
        socket.setSendBufferSize(1 << 20);
        serverAddr = InetAddress.getByName(serverIP);
    }

    private boolean enableMic() {
        if (GlobalMicController.getInstance().requestMicAccess(this)) {
            try {
                if (mic == null || !mic.isOpen()) {
                    initMic();
                }

                // Warm-up mic
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                micEnabled = true;
                System.out.println(clientID + ": Micro is now ON.");
                return true;
            } catch (LineUnavailableException e) {
                System.err.println("Lỗi: Không thể mở mic dù đã được cấp quyền.");
                GlobalMicController.getInstance().releaseMicAccess(this);
                return false;
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "Micro đang được sử dụng ở một phòng khác!",
                        "Xung đột Micro",
                        JOptionPane.WARNING_MESSAGE);
            });
            micEnabled = false;
            return false;
        }
    }

    private void disableMic() {
        GlobalMicController.getInstance().releaseMicAccess(this);
        synchronized (this) {
            if (mic != null && mic.isOpen()) {
                mic.stop();
                mic.close();
                System.out.println(clientID + ": Mic resource released.");
            }
        }
        micEnabled = false;
        System.out.println(clientID + ": Micro is now OFF.");
    }

    public boolean toggleMic() {
        if (micEnabled) {
            disableMic();
            System.out.println("🔇 Mic OFF");
            return false;
        } else {
            boolean ok = enableMic();
            if (ok) System.out.println("🎤 Mic ON");
            return ok;
        }
    }

    private void initMic() throws LineUnavailableException {
        AudioFormat format = getAudioFormat();
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(micInfo)) {
            throw new LineUnavailableException("Mic line not supported");
        }
        mic = (TargetDataLine) AudioSystem.getLine(micInfo);
        mic.open(format, BUFFER_SIZE * 2);
        mic.start();
        try {
            Thread.sleep(200);
            mic.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        running = false;
        disableMic();

        if (speakers != null && speakers.isOpen()) {
            speakers.stop();
            speakers.close();
            System.out.println("Speaker release");
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public void start() {
        try {
            initAudioLines();
            sendJoinPacket();
            startSending();
            startReceiving();
            startPlaying();
            startHeartbeatSender();
            startHeartbeatMonitor();
        } catch (LineUnavailableException e) {
            System.err.println("Không thể truy cập Micro hoặc Loa");
            stop();
        }
    }

    private void sendJoinPacket() {
        try {
            String joinMsg = "JOIN_ROOM:" + roomCode + ":" + clientID;
            byte[] joinData = joinMsg.getBytes();
            DatagramPacket pkt = new DatagramPacket(joinData, joinData.length, serverAddr, port);
            socket.send(pkt);
            System.out.println("📡 Sent JOIN_ROOM to server");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi JOIN_ROOM: " + e.getMessage());
        }
    }

    private void initAudioLines() throws LineUnavailableException {
        AudioFormat format = getAudioFormat();
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
        speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speakers.open(format, BUFFER_SIZE * 4);
        speakers.start();
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
                        DatagramPacket heartbeatPkt = new DatagramPacket(HEARTBEAT_DATA, HEARTBEAT_DATA.length, serverAddr, port);
                        socket.send(heartbeatPkt);
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
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
                    if (Arrays.equals(receivedData, HEARTBEAT_DATA)) continue;

                    String asText = new String(receivedData).trim();
                    if (asText.startsWith("SYNC:")) continue;

                    if (receivedData.length <= 72) continue;

                    String roomCodeFrame = new String(Arrays.copyOfRange(receivedData, 0, 36)).trim();
                    byte[] audioData = Arrays.copyOfRange(receivedData, 72, receivedData.length);

                    if (!roomCodeFrame.equals(this.roomCode)) continue;
                    if (audioData.length > 0) jitterBuffer.offer(audioData);
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
            System.out.println("Luồng nhận audio đã dừng.");
        }, "Audio-Receiver").start();
    }

    private void startPlaying() {
        new Thread(() -> {
            while (running) {
                try {
                    if (jitterBuffer.size() >= JITTER_BUFFER_MIN_SIZE) {
                        byte[] data = jitterBuffer.poll();
                        if (data != null) speakers.write(data, 0, data.length);
                    } else {
                        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                } catch (Exception e) {
                    if (running) System.err.println("Lỗi phát audio: " + e.getMessage());
                }
            }
            System.out.println("Luồng phát audio đã dừng.");
        }, "Audio-Player").start();
    }

    private void startHeartbeatSender() {
        new Thread(() -> {
            while (running) {
                try {
                    DatagramPacket hb = new DatagramPacket(HEARTBEAT_DATA, HEARTBEAT_DATA.length, serverAddr, port);
                    socket.send(hb);
                    try { Thread.sleep(HEARTBEAT_INTERVAL); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                } catch (Exception e) {
                    if (running) System.err.println("⚠️ Lỗi gửi heartbeat: " + e.getMessage());
                }
            }
        }, "Audio-HB-Send").start();
    }

    private void startHeartbeatMonitor() {
        new Thread(() -> {
            while (running) {
                try {
                    if (System.currentTimeMillis() - lastResponseTime > HEARTBEAT_TIMEOUT) {
                        notifyDisconnect("AUDIO", null);
                        break;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Audio-HB-Mon").start();
    }

    private void notifyDisconnect(String type, Exception e) {
        if (disconnectedHandled) return;
        disconnectedHandled = true;

        System.err.println("🔌 Mất kết nối tới " + type + " server"
                + (e != null ? ": " + e.getMessage() : ""));

        running = false;

        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onServerDisconnected(type));
        }
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
