package server;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class VideoServerUDP {
    private DatagramSocket socket;
    private int port = 5000;
    
    // Lưu địa chỉ client để phát video cho tất cả
    private CopyOnWriteArrayList<InetSocketAddress> clients = new CopyOnWriteArrayList<>();

    public VideoServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("Video UDP Server started on port " + port);
        byte[] buf = new byte[65536]; // buffer lớn

        while (true) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);
            System.out.println("📩 Received " + pkt.getLength() + " bytes from " + pkt.getAddress());
            // Lưu client vào danh sách nếu chưa có
            InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
            if (!clients.contains(clientAddr)) {
                clients.add(clientAddr);
                System.out.println("New client: " + clientAddr);
            }
            // Phát lại frame cho tất cả client (trừ client gửi)
            for (InetSocketAddress c : clients) {
                if (!c.equals(clientAddr)) {
                    DatagramPacket sendPkt = new DatagramPacket(
                        pkt.getData(), pkt.getLength(), c.getAddress(), c.getPort()
                    );
                    socket.send(sendPkt);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new VideoServerUDP();
    }
}
