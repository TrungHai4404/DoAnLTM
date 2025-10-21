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
                // 1) Heartbeat cũ (không header) -> có thể echo hoặc bỏ qua
                if (data.length == HEARTBEAT.length && Arrays.equals(data, HEARTBEAT)) {
                    // tùy bạn: echo lại hoặc chỉ continue
                    // socket.send(new DatagramPacket(data, data.length, pkt.getAddress(), pkt.getPort()));
                    continue;
                }
                // 2) Gói có header room+client (>=72 bytes)
                if (data.length >= 72) {
                    String roomCode = new String(Arrays.copyOfRange(data, 0, 36)).trim();
                    String clientID = new String(Arrays.copyOfRange(data, 36, 72)).trim();
                    byte[] audio = Arrays.copyOfRange(data, 72, data.length);

                    InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                    roomClients.putIfAbsent(roomCode, new CopyOnWriteArrayList<>());

                    // ✅ luôn ĐĂNG KÝ trước (kể cả keepalive 1 byte)
                    CopyOnWriteArrayList<InetSocketAddress> list = roomClients.get(roomCode);
                    if (!list.contains(clientAddr)) {
                        list.add(clientAddr);
                        System.out.println("Registered " + clientAddr + " into room " + roomCode + " (from " + clientID + ")");
                    }

                    // ✅ keepalive có header: payload rất nhỏ -> KHÔNG broadcast
                    if (audio.length <= 1) {
                        continue;
                    }

                    // 3) Audio thật -> broadcast cho các client khác trong cùng room
                    for (InetSocketAddress c : list) {
                        if (!c.equals(clientAddr)) {
                            final byte[] outData = data; // capture effectively final
                            pool.submit(() -> {
                                try {
                                    DatagramPacket sendPkt = new DatagramPacket(outData, outData.length, c.getAddress(), c.getPort());
                                    socket.send(sendPkt);
                                } catch (Exception e) {
                                    System.err.println("Lỗi gửi tới client " + c + ": " + e.getMessage());
                                }
                            });
                        }
                    }

                    continue;
                }
            }catch (SocketTimeoutException e) {
                // bỏ qua
            } catch (IOException e) {
                System.err.println("Mất kết nối tới Audio Server: " + e.getMessage());
            }    
        }
    }
}