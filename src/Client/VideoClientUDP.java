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
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }
    public VideoClientUDP(String serverIP) throws Exception {
        serverAddr = InetAddress.getByName(serverIP);
        socket = new DatagramSocket();
        socket.setSoTimeout(3000); // nhận không timeout
        socket.setReceiveBufferSize(1024 * 1024); // 1MB buffer nhận
        socket.setSendBufferSize(1024 * 1024);    // 1MB buffer gửi
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
            return packet;

        } catch (SocketTimeoutException e) {
            // Không nhận được gói → bỏ qua, tiếp tục vòng lặp
        } catch (SocketException e) {
            System.err.println("⚠️ Mất kết nối tới Video Server: " + e.getMessage());
            if (listener != null)
                SwingUtilities.invokeLater(() -> listener.onServerDisconnected("VIDEO"));
        } catch (IOException e) {
            System.err.println("⚠️ Lỗi I/O khi nhận frame: " + e.getMessage());
            if (listener != null)
                SwingUtilities.invokeLater(() -> listener.onServerDisconnected("VIDEO"));
        } catch (Exception e) {
            System.err.println("❌ Lỗi không xác định khi nhận frame: " + e.getMessage());
        }
        return null;
    }


    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("UDP video socket closed.");
        }
    }
}
