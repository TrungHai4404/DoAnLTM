package server;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class VideoServerUDP {
    private DatagramSocket socket;
    private int port = 5000;
    
    // Lưu địa chỉ client để phát video cho tất cả
    private CopyOnWriteArrayList<InetSocketAddress> clients = new CopyOnWriteArrayList<>();
    private ExecutorService pool = Executors.newFixedThreadPool(10);

    public VideoServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(2 * 1024 * 1024);
        socket.setSendBufferSize(2 * 1024 * 1024);
        System.out.println("Video UDP Server started on port " + port);
        byte[] buf = new byte[65536]; // buffer lớn

        while (true) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);

            String data = new String(pkt.getData(), 0, pkt.getLength()).trim();
            // Kiểm tra nếu gói tin là heartbeat
            if (new String(pkt.getData(), 0, pkt.getLength()).equals("PING")) {
                // Trả lại gói heartbeat (echo) để client cập nhật lastResponseTime
                DatagramPacket reply = new DatagramPacket(
                    pkt.getData(), pkt.getLength(), pkt.getAddress(), pkt.getPort()
                );
                socket.send(reply);
                continue; // bỏ qua không phát tán
            }
            // ✅ Kiểm tra gói PING
            if (data.equalsIgnoreCase("PING_VIDEO")) {
                byte[] pong = "PONG_VIDEO".getBytes();
                DatagramPacket resp = new DatagramPacket(pong, pong.length, pkt.getAddress(), pkt.getPort());
                socket.send(resp);
                System.out.println("↩️ PONG_VIDEO sent to " + pkt.getAddress());
                continue;
            }
            // Lưu client vào danh sách nếu chưa có
            InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
            if (!clients.contains(clientAddr)) {
                clients.add(clientAddr);
                System.out.println("New client: " + clientAddr);
            }
            // Phát lại frame cho tất cả client (trừ client gửi)
            byte[] datasend = Arrays.copyOf(pkt.getData(), pkt.getLength());

            // broadcast tới các client khác
            for (InetSocketAddress c : clients) {
                if (!c.equals(clientAddr)) {
                    pool.submit(() -> {
                        try {
                            DatagramPacket p = new DatagramPacket(datasend, datasend.length, c.getAddress(), c.getPort());
                            socket.send(p);
                        } catch (Exception e) {
                            System.err.println("Lỗi gửi tới client " + c + ": " + e.getMessage());
                        }
                    });
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new VideoServerUDP();
    }
}
