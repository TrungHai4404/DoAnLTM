package server;

import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AudioServerUDP {
    private DatagramSocket socket;
    private int port = 5001;
    private CopyOnWriteArrayList<InetSocketAddress> clients = new CopyOnWriteArrayList<>();
    private static final int PACKET_SIZE = 4096;

    public AudioServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("🎧 Audio UDP Server started on port " + port);

        byte[] buffer = new byte[PACKET_SIZE];

        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                socket.receive(pkt);

                InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                if (!clients.contains(clientAddr)) {
                    clients.add(clientAddr);
                    System.out.println("New audio client: " + clientAddr);
                }

                // Phát lại cho tất cả client khác
                for (InetSocketAddress c : clients) {
                    if (!c.equals(clientAddr)) {
                        DatagramPacket sendPkt = new DatagramPacket(
                                pkt.getData(), pkt.getLength(), c.getAddress(), c.getPort());
                        socket.send(sendPkt);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new AudioServerUDP();
    }
}
