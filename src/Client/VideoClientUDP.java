
package Client;
import java.net.*;
import java.util.Arrays;

public class VideoClientUDP {
    private DatagramSocket socket;
    private InetAddress serverIP;
    private int serverPort = 5000;

    public VideoClientUDP(String server) throws Exception {
        socket = new DatagramSocket();
        serverIP = InetAddress.getByName(server);
    }

    public void sendFrame(byte[] data) throws Exception {
        DatagramPacket packet = new DatagramPacket(data, data.length, serverIP, serverPort);
        socket.send(packet);
    }

    public byte[] receiveFrame() throws Exception {
        byte[] buffer = new byte[65507];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }
}