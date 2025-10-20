package server;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioServerUDP {
    private DatagramSocket socket;
    private int port = 5001;
    private static final byte[] HEARTBEAT = "HBEAT".getBytes();
    private ExecutorService pool = Executors.newFixedThreadPool(10);

    private final CopyOnWriteArrayList<InetSocketAddress> clients = new CopyOnWriteArrayList<>();
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
                
                String dataP = new String(pkt.getData(), 0, pkt.getLength()).trim();
                // ✅ Kiểm tra gói PING
                if (dataP.equalsIgnoreCase("PING_AUDIO")) {
                    byte[] pong = "PONG_AUDIO".getBytes();
                    DatagramPacket resp = new DatagramPacket(pong, pong.length, pkt.getAddress(), pkt.getPort());
                    socket.send(resp);
                    System.out.println("↩️ PONG_AUDIO sent to " + pkt.getAddress());
                    continue;
                }

                InetSocketAddress clientAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                if (!clients.contains(clientAddr)) {
                    clients.add(clientAddr);
                    System.out.println("New audio client: " + clientAddr);
                }
                
                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                // ⚡ Xử lý Heartbeat
                if (data.length == HEARTBEAT.length && Arrays.equals(data, HEARTBEAT)) {
                    DatagramPacket echo = new DatagramPacket(data, data.length, pkt.getAddress(), pkt.getPort());
                    socket.send(echo);
                    // System.out.println("💓 Echo HBEAT -> " + sender);
                    continue;
                }
                // Phát lại cho tất cả client khác
                for (InetSocketAddress c : clients) {
                    if (!c.equals(clientAddr)) {
                        pool.submit(() -> {
                            try {
                                DatagramPacket sendPkt = new DatagramPacket(data, data.length, c.getAddress(), c.getPort());
                                socket.send(sendPkt);
                            } catch (Exception e) {
                                System.err.println("⚠️ Lỗi gửi tới client " + c + ": " + e.getMessage());
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
