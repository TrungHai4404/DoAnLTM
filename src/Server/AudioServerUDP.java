package server;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioServerUDP {
    private DatagramSocket socket;
    private int port = 5001;
    private ExecutorService pool = Executors.newFixedThreadPool(20);

    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<InetSocketAddress>> roomClients = new ConcurrentHashMap<>();
    private final int BUFFER_SIZE = 2048;

    public AudioServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(1 << 20);
        socket.setSendBufferSize(1 << 20);
        System.out.println("Audio Server started on port " + port);
        while (true) {
            try{
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                
                
                // ✅ Kiểm tra gói PING
                String dataP = new String(pkt.getData(), 0, pkt.getLength()).trim();
                if (dataP.equalsIgnoreCase("PING_AUDIO")) {
                    byte[] pong = "PONG_AUDIO".getBytes();
                    DatagramPacket resp = new DatagramPacket(pong, pong.length, pkt.getAddress(), pkt.getPort());
                    socket.send(resp);
                    System.out.println("↩️ PONG_AUDIO sent to " + pkt.getAddress());
                    continue;
                }
                
                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                
                // Tách dữ liệu
                if (data.length < 72) continue;
                String roomCode = new String(Arrays.copyOfRange(data, 0, 36)).trim();
                String clientID = new String(Arrays.copyOfRange(data, 36, 72)).trim();
                byte[] audio = Arrays.copyOfRange(data, 72, data.length);
                if (roomCode.isEmpty()) continue;
                InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                CopyOnWriteArrayList<InetSocketAddress> clientsInRoom = roomClients.computeIfAbsent(
                    roomCode, k -> new CopyOnWriteArrayList<>()
                );
                
                if (clientsInRoom.addIfAbsent(clientAddr)) {
                    System.out.println("New audio client in room [" + roomCode + "]: " + clientAddr);
                }

                // Phát lại cho tất cả client khác
                if (audio.length > 0) {
                    // Nếu là audio thật -> Broadcast
                    broadcast(roomCode, clientAddr, data);
                } else {
                    // Nếu là gói "ping" (mic-tắt, audio.length == 0)
                    // -> Gửi echo lại (gói 72-byte + 0 payload)
                    // để client cập nhật lastResponseTime
                    DatagramPacket echo = new DatagramPacket(data, data.length, clientAddr.getAddress(), clientAddr.getPort());
                    socket.send(echo);
                }
            }catch (SocketTimeoutException e) {
                // bỏ qua
            } catch (IOException e) {
                System.err.println("Mất kết nối tới Audio Server: " + e.getMessage());
            }    
        }
    }

    private void broadcast(String roomCode, InetSocketAddress senderAddr, byte[] data) {
        CopyOnWriteArrayList<InetSocketAddress> clientsInRoom = roomClients.get(roomCode);
        if (clientsInRoom == null) return;
        
        for (InetSocketAddress c : clientsInRoom) {
            if (!c.equals(senderAddr)) {
                pool.submit(() -> {
                    try {
                        DatagramPacket sendPkt = new DatagramPacket(data, data.length, c.getAddress(), c.getPort());
                        socket.send(sendPkt);
                    } catch (Exception e) {
                        System.err.println("Lỗi gửi tới client " + c + ": " + e.getMessage());
                        clientsInRoom.remove(c); // Xóa client nếu gửi lỗi
                    }
                });
            }
        }
    }
}
