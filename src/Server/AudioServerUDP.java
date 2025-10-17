package server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class AudioServerUDP {

    private final int port = 5001;
    // Thời gian timeout cho client (15 giây không hoạt động sẽ bị xóa)
    private final long CLIENT_TIMEOUT_MS = 15000; 

    private DatagramSocket socket;
    
    // 💡 TỐI ƯU 1: Dùng ConcurrentHashMap để lưu client và thời gian hoạt động cuối
    // Key: Địa chỉ client, Value: Thời gian (ms) cuối cùng nhận được packet
    private final ConcurrentHashMap<InetSocketAddress, Long> clients = new ConcurrentHashMap<>();

    public AudioServerUDP() throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("🎧 Audio UDP Server started on port " + port);

        // 💡 SỬA LỖI: Bắt đầu một luồng riêng để dọn dẹp các client không hoạt động
        startClientCleanupThread();

        // Luồng chính chỉ tập trung vào việc nhận và phát lại packet
        listenForPackets();
    }

    private void listenForPackets() {
        byte[] buffer = new byte[4096]; // Buffer cho dữ liệu âm thanh

        while (true) {
            try {
                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivedPacket);

                InetSocketAddress clientAddr = new InetSocketAddress(
                    receivedPacket.getAddress(),
                    receivedPacket.getPort()
                );

                // Cập nhật thời gian hoạt động hoặc thêm client mới
                if (!clients.containsKey(clientAddr)) {
                    System.out.println("✅ New audio client connected: " + clientAddr);
                }
                clients.put(clientAddr, System.currentTimeMillis());

                // Phát lại packet cho tất cả các client khác
                broadcastPacket(receivedPacket, clientAddr);

            } catch (Exception e) {
                System.err.println("Error receiving packet: " + e.getMessage());
                // e.printStackTrace(); // Bật lên để debug nếu cần
            }
        }
    }
    
    private void broadcastPacket(DatagramPacket packet, InetSocketAddress senderAddress) {
        // Duyệt qua tất cả các client đang kết nối
        for (InetSocketAddress clientAddress : clients.keySet()) {
            // Không gửi lại cho chính người đã gửi
            if (!clientAddress.equals(senderAddress)) {
                try {
                    // Tạo packet mới để gửi đi
                    DatagramPacket sendPacket = new DatagramPacket(
                        packet.getData(),
                        packet.getLength(),
                        clientAddress
                    );
                    socket.send(sendPacket);
                } catch (Exception e) {
                     System.err.println("Error broadcasting to " + clientAddress + ": " + e.getMessage());
                }
            }
        }
    }

    private void startClientCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    // Quét 5 giây một lần
                    Thread.sleep(5000); 
                    long currentTime = System.currentTimeMillis();

                    // Lặp qua danh sách clients và xóa những ai đã timeout
                    clients.forEach((address, lastSeenTime) -> {
                        if (currentTime - lastSeenTime > CLIENT_TIMEOUT_MS) {
                            clients.remove(address);
                            System.out.println("🗑️ Removed inactive audio client: " + address);
                        }
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Phục hồi trạng thái interrupt
                    System.err.println("Cleanup thread was interrupted.");
                }
            }
        });
        
        cleanupThread.setDaemon(true); // Đặt làm daemon để nó tự kết thúc khi chương trình chính dừng
        cleanupThread.start();
    }

    public static void main(String[] args) throws Exception {
        new AudioServerUDP();
    }
}