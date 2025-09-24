package Client;

import java.net.*;

public class VideoClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int port = 5000;

    public VideoClientUDP(String serverIP) throws Exception {
        socket = new DatagramSocket();
        serverAddr = InetAddress.getByName(serverIP);
    }

    /** Gửi frame kèm clientID */
    public void sendFrame(byte[] frameData, String clientID) throws Exception {
        byte[] clientBytes = clientID.getBytes();
        byte[] data = new byte[36 + frameData.length];

        System.arraycopy(clientBytes, 0, data, 0, Math.min(clientBytes.length, 36));
        System.arraycopy(frameData, 0, data, 36, frameData.length);

        DatagramPacket pkt = new DatagramPacket(data, data.length, serverAddr, port);
        socket.send(pkt);
    }

    /** Nhận frame từ server */
    public DatagramPacket receiveFrame(byte[] buf) throws Exception {
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        socket.receive(pkt);
        return pkt;
    }
}
