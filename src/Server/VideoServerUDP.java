package videocall.server;

import java.net.*;
import java.util.*;

public class VideoServerUDP {
    private DatagramSocket socket;
    private int port = 5000;
    private List<InetSocketAddress> clients = new ArrayList<>();

    public VideoServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("UDP Video Server started on port " + port);

        byte[] buffer = new byte[65507];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
            if (!clients.contains(clientAddress)) clients.add(clientAddress);

            // Relay packet to all other clients
            for (InetSocketAddress c : clients) {
                if (!c.equals(clientAddress)) {
                    DatagramPacket relay = new DatagramPacket(
                        packet.getData(), packet.getLength(), c.getAddress(), c.getPort());
                    socket.send(relay);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new VideoServerUDP();
    }
}
