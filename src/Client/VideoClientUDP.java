package Client;

import java.net.*;

public class VideoClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int serverPort = 5000;
    private String clientID;

    public VideoClientUDP(String serverIP, String clientID) throws Exception {
        this.serverAddr = InetAddress.getByName(serverIP);
        this.clientID = clientID;
        socket = new DatagramSocket();
    }

    public void sendFrame(byte[] frame) throws Exception {
        byte[] idBytes = clientID.getBytes(); // 36 byte
        byte[] data = new byte[idBytes.length + frame.length];
        System.arraycopy(idBytes, 0, data, 0, idBytes.length);
        System.arraycopy(frame, 0, data, idBytes.length, frame.length);

        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, serverPort);
        socket.send(packet);
    }

    public DatagramPacket receiveFrame(byte[] buffer) throws Exception {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return packet;
    }
}
