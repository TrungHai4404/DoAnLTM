package Client;

import java.net.*;

public class VideoClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5000;

    public VideoClientUDP(String serverIP) throws Exception {
        serverAddr = InetAddress.getByName(serverIP);
        socket = new DatagramSocket();
        socket.setSoTimeout(0); // nhận không timeout
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
        } catch (SocketTimeoutException ignored) {
        } catch (Exception e) {
            System.err.println("Lỗi nhận frame: " + e.getMessage());
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
