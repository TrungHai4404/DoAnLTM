package server;

import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AudioServerUDP {
    private DatagramSocket socket;
    private int port = 5001;
    private CopyOnWriteArrayList<InetSocketAddress> clients = new CopyOnWriteArrayList<>();
    private final int PACKET_SIZE = 1024 + 4;

    public AudioServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("🎧 Audio UDP Server started on port " + port);

        byte[] buf = new byte[PACKET_SIZE];

        while (true) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);

            InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
            if (!clients.contains(clientAddr)) {
                clients.add(clientAddr);
                System.out.println("New audio client: " + clientAddr);
            }

            // Phát lại cho tất cả client khác
            for (InetSocketAddress c : clients) {
                if (!c.equals(clientAddr)) {
                    DatagramPacket sendPkt = new DatagramPacket(pkt.getData(), pkt.getLength(), c.getAddress(), c.getPort());
                    socket.send(sendPkt);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new AudioServerUDP();
    }
}
