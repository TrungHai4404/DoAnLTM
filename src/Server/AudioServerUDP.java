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
    private static final byte[] HEARTBEAT = "HBEAT".getBytes();
    private ExecutorService pool = Executors.newFixedThreadPool(10);

    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<InetSocketAddress>> roomClients = new ConcurrentHashMap<>();
    private final int BUFFER_SIZE = 1024;

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
                // ⚡ Xử lý Heartbeat
                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                if (data.length == HEARTBEAT.length && Arrays.equals(data, HEARTBEAT)) {
                    DatagramPacket echo = new DatagramPacket(data, data.length, pkt.getAddress(), pkt.getPort());
                    socket.send(echo);
                    continue;
                }
                // Tách dữ liệu
                if (data.length <= 72) continue;
                String roomCode = new String(Arrays.copyOfRange(data, 0, 36)).trim();
                String clientID = new String(Arrays.copyOfRange(data, 36, 72)).trim();
                byte[] audio = Arrays.copyOfRange(data, 72, data.length);
                
                InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                roomClients.putIfAbsent(roomCode, new CopyOnWriteArrayList<>());
                
                if (!roomClients.get(roomCode).contains(clientAddr)) {
                    roomClients.get(roomCode).add(clientAddr);
                    System.out.println("New client in room [" + roomCode + "]: " + clientAddr);
                }

                // Phát lại cho tất cả client khác
                for (InetSocketAddress c : roomClients.get(roomCode)) {
                    if (!c.equals(clientAddr)) {
                        pool.submit(() -> {
                            try {
                                DatagramPacket sendPkt = new DatagramPacket(data, data.length, c.getAddress(), c.getPort());
                                socket.send(sendPkt);
                            } catch (Exception e) {
                                System.err.println("Lỗi gửi tới client " + c + ": " + e.getMessage());
                            }
                        });
                    }
                }
            }catch (SocketTimeoutException e) {
                // bỏ qua
            } catch (IOException e) {
                System.err.println("Mất kết nối tới Audio Server: " + e.getMessage());
            }    
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            new AudioServerUDP();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
