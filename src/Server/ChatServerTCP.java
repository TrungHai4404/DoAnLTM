package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.concurrent.CopyOnWriteArrayList;
import Server.MyConnection;
public class ChatServerTCP {
    private int port = 6000;
    private CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public ChatServerTCP() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("💬 Chat TCP Server started on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            new Thread(handler).start();
        }
    }

    class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

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
                    System.out.println("📩 Nhận tin: " + msg);

                    // Nếu client gửi EXIT:<userID>|<roomCode>
                    if (msg.startsWith("EXIT:")) {
                        handleExit(msg);
                        break;
                    } else {
                        broadcast(msg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                clients.remove(this);
                try { socket.close(); } catch (Exception ignored) {}
            }
        }

        /** Xử lý khi user rời phòng **/
        private void handleExit(String msg) {
            try {
                // Cú pháp: EXIT:<userID>|<roomCode>
                String[] parts = msg.substring(5).split("\\|");
                if (parts.length < 2) return;

                String userID = parts[0].trim();
                String roomCode = parts[1].trim();

                // Gửi thông báo cho các client khác
                broadcast("EXIT:" + userID);

                // Cập nhật thời gian rời phòng trong DB
                updateLeaveTime(userID, roomCode);

                System.out.println("👋 Người dùng rời phòng: " + userID + " | Phòng: " + roomCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /** Cập nhật LeaveTime vào database **/
        private void updateLeaveTime(String userID, String roomCode) {
            String sql = """
                UPDATE RoomMembers
                SET LeaveTime = GETDATE()
                WHERE UserID = ? AND RoomID = (SELECT RoomID FROM VideoRooms WHERE RoomCode = ?)
                  AND LeaveTime IS NULL
            """;

            try (Connection conn = MyConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setObject(1, java.util.UUID.fromString(userID));
                ps.setString(2, roomCode);
                ps.executeUpdate();

                System.out.println("🕓 Cập nhật thời gian rời phòng thành công.");
            } catch (Exception e) {
                System.err.println("⚠️ Lỗi cập nhật LeaveTime trong DB:");
                e.printStackTrace();
            }
        }

        /** Gửi tin nhắn cho tất cả client **/
        private void broadcast(String msg) {
            for (ClientHandler c : clients) {
                try {
                    c.out.println(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatServerTCP();
    }
}
