package server;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class VideoServerUDP {
    private DatagramSocket socket;
    private int port = 5000;

    // roomID -> list clients
    private Map<String, CopyOnWriteArrayList<InetSocketAddress>> rooms = new ConcurrentHashMap<>();

    public VideoServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("UDP Video Server started on port " + port);

        byte[] buffer = new byte[65507];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            // Data format: roomID|clientID|<video bytes>
            String header = new String(packet.getData(), 0, Math.min(packet.getLength(), 100));
            String[] parts = header.split("\\|", 3);
            if (parts.length < 3) continue;
            String roomID = parts[0];
            String clientID = parts[1];
            byte[] videoData = parts[2].getBytes(); // simple demo, client gửi text+video bytes

            InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
            rooms.putIfAbsent(roomID, new CopyOnWriteArrayList<>());
            CopyOnWriteArrayList<InetSocketAddress> clients = rooms.get(roomID);
            if (!clients.contains(sender)) clients.add(sender);

            // Relay packet to all clients in same room except sender
            for (InetSocketAddress c : clients) {
                if (!c.equals(sender)) {
                    DatagramPacket relay = new DatagramPacket(
                        Arrays.copyOf(packet.getData(), packet.getLength()),
                        packet.getLength(), c.getAddress(), c.getPort());
                    socket.send(relay);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new VideoServerUDP();
    }
}
