package server;

import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AudioServerUDP {
    private DatagramSocket socket;
    private int port = 5001;
    private CopyOnWriteArrayList<InetSocketAddress> clients = new CopyOnWriteArrayList<>();
    private final int BUFFER_SIZE = 1024;

    public AudioServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(65536);
        socket.setSendBufferSize(65536);
        System.out.println("🎧 Audio UDP Server started on port " + port);

        byte[] buf = new byte[BUFFER_SIZE];

        while (true) {
            try {
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new AudioServerUDP();
    }
}
