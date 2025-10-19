package server;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AudioServerUDP {
    private DatagramSocket socket;
    private int port = 5001;
    private CopyOnWriteArrayList<InetSocketAddress> clients = new CopyOnWriteArrayList<>();
    private final int BUFFER_SIZE = 1024;

    public AudioServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("🎧 Audio Server started on port " + port);

        byte[] buf = new byte[BUFFER_SIZE];

        while (true) {
            try{
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                String data = new String(pkt.getData(), 0, pkt.getLength()).trim();

                // ✅ Kiểm tra gói PING
                if (data.equalsIgnoreCase("PING_AUDIO")) {
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

                // Phát lại cho tất cả client khác
                for (InetSocketAddress c : clients) {
                    if (!c.equals(clientAddr)) {
                        DatagramPacket sendPkt = new DatagramPacket(pkt.getData(), pkt.getLength(), c.getAddress(), c.getPort());
                        socket.send(sendPkt);
                    }
                }
            }catch (SocketTimeoutException e) {
                // bỏ qua
            } catch (IOException e) {
                System.err.println("⚠️ Mất kết nối tới Audio Server: " + e.getMessage());
                handleServerDisconnect("AUDIO");
            }
            
            
            
        }
    }

    public static void main(String[] args) throws Exception {
        new AudioServerUDP();
    }

    private void handleServerDisconnect(String audio) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
