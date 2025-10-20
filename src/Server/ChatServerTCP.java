package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import Server.MyConnection;
import Utils.CryptoUtils;
import dao.VideoRoomDao;

public class ChatServerTCP {
    private int port = 6000;
    //Quản lý client theo từng phòng
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ClientHandler>> rooms = new ConcurrentHashMap<>();
    //Quản lý trạng thái toàn sv
    private ConcurrentHashMap<String, Boolean> cameraStates = new ConcurrentHashMap<>();
    
    private VideoRoomDao roomDao = new VideoRoomDao();
    public ChatServerTCP() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Chat TCP Server started on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            new Thread(handler).start();
        }
    }

    class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private String roomCode;

        public ClientHandler(Socket s) throws IOException {
            socket = s;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }
        @Override
        public void run() {
            try {
                String msg;

                while ((msg = in.readLine()) != null) {
                    String decrypted = msg;

                    // Giải mã chỉ khi là tin nhắn chat
                    if (!msg.startsWith("JOIN:")
                            && !msg.startsWith("EXIT:")
                            && !msg.startsWith("CAM_ON:")
                            && !msg.startsWith("CAM_OFF:")
                            && !msg.startsWith("JOINED:")
                            && !msg.startsWith("ERROR:")) {
                        try {
                            decrypted = CryptoUtils.decrypt(msg);
                        } catch (Exception e) {
                            System.err.println("Không thể giải mã tin nhắn: " + msg);
                            continue;
                        }
                    }

                    System.out.println("Nhận từ client (" + username + "): " + decrypted);

                    if (decrypted.startsWith("JOIN:")) {
                        handleJoin(decrypted);
                    }else if (decrypted.startsWith("CAM_ON:")) {
                        cameraStates.put(this.username, true);
                        broadcastToRoomAndSelf(decrypted);
                    }else if (decrypted.startsWith("CAM_OFF:")) {
                        cameraStates.put(this.username, false);
                        broadcastToRoomAndSelf(decrypted);
                    }else if (decrypted.startsWith("EXIT:")) {
                        handleExit(decrypted);
                        break;
                    }else{
                        // Chat thường → broadcast có mã hóa
                        broadcastToRoomAndSelf(decrypted);
                    }
                    
                }

            } catch(SocketException se){
                System.err.println(" Client " + username + " disconected: " + se.getMessage());
            }catch (Exception e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }
        // Dọn dẹp khi client ngắt kết nối
        private void cleanup() {
            if (this.roomCode != null && rooms.containsKey(this.roomCode)) {
                rooms.get(this.roomCode).remove(this);
                // Nếu phòng trống, có thể xóa phòng khỏi map
                if (rooms.get(this.roomCode).isEmpty()) {
                    rooms.remove(this.roomCode);
                    System.out.println("Room [" + this.roomCode + "] is now empty and removed.");
                }
            }
            if (this.username != null) {
                cameraStates.remove(this.username);
            }
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
        // Gửi 1 tin cụ thể
        private void sendEncrypted(String plainMsg) {
            try {
                boolean isSystemMsg = plainMsg.startsWith("JOIN:")
                        || plainMsg.startsWith("EXIT:")
                        || plainMsg.startsWith("CAM_ON:")
                        || plainMsg.startsWith("CAM_OFF:")
                        || plainMsg.startsWith("JOINED:")
                        || plainMsg.startsWith("ERROR:");

                if (isSystemMsg) {
                    out.println(plainMsg);
                } else {
                    String encrypted = CryptoUtils.encrypt(plainMsg);
                    out.println(encrypted);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Broadcast cho tất cả client trong cùng phòng
        private void broadcastToRoom(String plainMsg) {
            // Lấy danh sách client trong phòng của người gửi tin này
            CopyOnWriteArrayList<ClientHandler> clientsInRoom = rooms.get(this.roomCode);

            if (clientsInRoom == null) return; // Phòng không tồn tại thì không làm gì

            for (ClientHandler client : clientsInRoom) {
                // Chỉ gửi cho người khác, không gửi lại cho chính mình
                if (client != this) {
                    client.sendEncrypted(plainMsg);
                }
            }
        }
        // Gửi tin cho tất cả mọi người trong phòng, BAO GỒM cả người gửi
        private void broadcastToRoomAndSelf(String plainMsg) {
            if (this.roomCode == null) return;
            CopyOnWriteArrayList<ClientHandler> clientsInRoom = rooms.get(this.roomCode);
            if (clientsInRoom == null) return;
            
            for (ClientHandler client : clientsInRoom) {
                client.sendEncrypted(plainMsg);
            }
        }   

        private void handleExit(String plainMsg) {
            try {
                String[] parts = plainMsg.substring(5).split("\\|");
                if (parts.length < 2) return;
                String username = parts[0].trim();
                String roomCode = parts[1].trim();

                broadcastToRoom("EXIT:" + username + "|" + roomCode);         
                try {
                    roomDao.markLeave(this.username, this.roomCode);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                System.out.println(username + " đã rời phòng " + roomCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        private void handleJoin(String joinMsg) {
            String[] parts = joinMsg.substring(5).split("\\|");
            if (parts.length < 2) return;
            
            this.username = parts[0].trim();
            this.roomCode = parts[1].trim();

            // Lấy hoặc tạo phòng mới (thread-safe)
            CopyOnWriteArrayList<ClientHandler> clientsInRoom = rooms.computeIfAbsent(this.roomCode, k -> new CopyOnWriteArrayList<>());
            clientsInRoom.add(this);

            System.out.println("✔️ " + this.username + " has joined room [" + this.roomCode + "]");

            // Gửi thông báo có người mới vào phòng (chỉ cho những người khác)
            broadcastToRoom("JOINED:" + this.username);
            
            // Gửi cho người mới vào trạng thái camera của tất cả mọi người trong phòng
            for (ClientHandler client : clientsInRoom) {
                boolean isCamOn = cameraStates.getOrDefault(client.username, false);
                String statusMsg = (isCamOn ? "CAM_ON:" : "CAM_OFF:") + client.username;
                this.sendEncrypted(statusMsg);
            }
        }
    }
}
