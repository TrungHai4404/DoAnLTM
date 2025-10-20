package server;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class VideoServerUDP {
    private DatagramSocket socket;
    private int port = 5000;
    
    // Lưu địa chỉ client để phát video cho tất cả
    private ExecutorService pool = Executors.newFixedThreadPool(10);
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<InetSocketAddress>> roomClients = new ConcurrentHashMap<>();

    public VideoServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(2 * 1024 * 1024);
        socket.setSendBufferSize(2 * 1024 * 1024);
        System.out.println("Video UDP Server started on port " + port);
        byte[] buf = new byte[65536]; // buffer lớn

        while (true) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);         
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
            String dataPing = new String(pkt.getData(), 0, pkt.getLength()).trim();
            if (dataPing.equalsIgnoreCase("PING_VIDEO")) {
                byte[] pong = "PONG_VIDEO".getBytes();
                DatagramPacket resp = new DatagramPacket(pong, pong.length, pkt.getAddress(), pkt.getPort());
                socket.send(resp);
                System.out.println("↩️ PONG_VIDEO sent to " + pkt.getAddress());
                continue;
            }
            // 🚀 Tách room + clientID + frame
            byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
            if (data.length <= 72) continue;
            String roomCode = new String(Arrays.copyOfRange(data, 0, 36)).trim();
            String clientID = new String(Arrays.copyOfRange(data, 36, 72)).trim();
            byte[] frame = Arrays.copyOfRange(data, 72, data.length);

            InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
            roomClients.putIfAbsent(roomCode, new CopyOnWriteArrayList<>());

            // Thêm client vào phòng nếu chưa có
            if (!roomClients.get(roomCode).contains(clientAddr)) {
                roomClients.get(roomCode).add(clientAddr);
                System.out.println("New client in room [" + roomCode + "]: " + clientAddr);
            }
            // Phát lại frame cho tất cả client (trừ client gửi)
            CopyOnWriteArrayList<InetSocketAddress> targets = roomClients.get(roomCode);
            for (InetSocketAddress c : targets) {
                if (!c.equals(clientAddr)) {
                    pool.submit(() -> {
                        try {
                            DatagramPacket p = new DatagramPacket(data, data.length, c.getAddress(), c.getPort());
                            socket.send(p);
                        } catch (Exception e) {
                            System.err.println("⚠️ Lỗi gửi tới client " + c + ": " + e.getMessage());
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
