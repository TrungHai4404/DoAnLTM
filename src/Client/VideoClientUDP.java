package Client;

import Client.AudioClientUDP.ConnectionListener;
import java.io.IOException;
import java.net.*;
import javax.swing.SwingUtilities;

public class VideoClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5000;
    private ConnectionListener listener;
    private static final byte[] HEARTBEAT_DATA = "PING".getBytes();
    private static final int HEARTBEAT_INTERVAL = 3000; // 3 giây
    private static final int HEARTBEAT_TIMEOUT = 9000;  // 9 giây không nhận → xem như mất kết nối
    private volatile boolean running = true;
    private long lastResponseTime = System.currentTimeMillis();
    private volatile boolean disconnectedHandled = false;

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }
    public VideoClientUDP(String serverIP) throws Exception {
        serverAddr = InetAddress.getByName(serverIP);
        socket = new DatagramSocket();
        socket.setSoTimeout(3000); // nhận không timeout
        socket.setReceiveBufferSize(2 * 1024 * 1024); // 1MB buffer nhận
        socket.setSendBufferSize(2 * 1024 * 1024);    // 1MB buffer gửi
        startHeartbeatSender();
        startHeartbeatMonitor();
    }

    /** Gửi frame kèm username (clientID) */
    public void sendFrame(byte[] frameData, String clientID) {
        try {
            byte[] idBytes = clientID.getBytes();
            byte[] data = new byte[36 + frameData.length];
            System.arraycopy(idBytes, 0, data, 0, Math.min(idBytes.length, 36));
            System.arraycopy(frameData, 0, data, 36, frameData.length);

            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, port);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("Gửi frame thất bại: " + e.getMessage());
        }
    }

    /** Nhận frame từ server */
    public DatagramPacket receiveFrame(byte[] buffer) {
        try {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            lastResponseTime = System.currentTimeMillis();

            return packet;

        } catch (SocketTimeoutException e) {
            // Không nhận được gói → bỏ qua, tiếp tục vòng lặp
        } catch (SocketException e) {
            notifyDisconnect("VIDEO", e);
        } catch (IOException e) {
            notifyDisconnect("VIDEO", e);
        } 
        return null;
    }
    private void startHeartbeatSender() {
        new Thread(() -> {
            while (running) {
                try {
                    DatagramPacket heartbeat = new DatagramPacket(
                        HEARTBEAT_DATA, HEARTBEAT_DATA.length, serverAddr, port
                    );
                    socket.send(heartbeat);
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (Exception ignored) {}
            }
        }, "Video-Heartbeat-Sender").start();
    }

    private void startHeartbeatMonitor() {
        new Thread(() -> {
            while (running) {
                try {
                    if (System.currentTimeMillis() - lastResponseTime > HEARTBEAT_TIMEOUT) {
                        notifyDisconnect("VIDEO", null);
                        break;
                    }
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
            }
        }, "Video-Heartbeat-Monitor").start();
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

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("UDP video socket closed.");
        }
    }
}
