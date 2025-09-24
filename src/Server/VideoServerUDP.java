package server;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VideoServerUDP {
    private int port = 5000;
    private DatagramSocket socket;
    // clientID -> InetSocketAddress
    private ConcurrentHashMap<String, InetSocketAddress> clients = new ConcurrentHashMap<>();

    public VideoServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("Video UDP Server started on port " + port);

        byte[] buffer = new byte[65507]; // max UDP size

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            // dữ liệu: clientID + frame
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            String clientID = new String(data, 0, 36); // giả sử 36 byte ID
            byte[] frame = Arrays.copyOfRange(data, 36, data.length);

            // Lưu client
            InetSocketAddress addr = new InetSocketAddress(packet.getAddress(), packet.getPort());
            clients.put(clientID, addr);

            // Broadcast frame tới tất cả client khác
            for (Map.Entry<String, InetSocketAddress> e : clients.entrySet()) {
                if (!e.getKey().equals(clientID)) {
                    DatagramPacket sendPacket = new DatagramPacket(frame, frame.length, e.getValue().getAddress(), e.getValue().getPort());
                    socket.send(sendPacket);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new VideoServerUDP();
    }
}
