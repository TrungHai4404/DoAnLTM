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
                    System.out.println("Nhan tin: " + msg);

                    // Nếu client gửi EXIT:<userID>|<roomCode>
                    if (msg.startsWith("EXIT:")) {
                        handleExit(msg); // đã broadcast trong handleExit
                        clients.remove(this); // remove client hiện tại
                        break; // kết thúc thread
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

        /** Xử lý khi user rời phòng và broadcast cho tất cả client khác **/
        private void handleExit(String msg) {
            try {
                // Cú pháp: EXIT:<username>|<roomCode>
                String[] parts = msg.substring(5).split("\\|");
                if (parts.length < 2) return;

                String username = parts[0].trim();
                String roomCode = parts[1].trim();

                // Phát lại cho tất cả client khác
                broadcast("EXIT:" + username + "|" + roomCode);

                // Cập nhật LeaveTime trong DB (tra UUID từ Username)
                updateLeaveTimeByUsername(username, roomCode);

                System.out.println("Nguoi dung roi phong: " + username + " | Phòng: " + roomCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void updateLeaveTimeByUsername(String username, String roomCode) {
            String sql = """
                UPDATE RoomMembers
                SET LeaveTime = GETDATE()
                WHERE UserID = (SELECT UserID FROM Users WHERE Username = ?)
                  AND RoomID = (SELECT RoomID FROM VideoRooms WHERE RoomCode = ?)
                  AND LeaveTime IS NULL
            """;

            try (Connection conn = MyConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, roomCode);
                ps.executeUpdate();
                System.out.println("Da cap nhat thoi gian roi phong " + username);
            } catch (Exception e) {
                System.err.println("Loi cap nhat thoi gian roi phong" + username);
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

        /** Gửi tin nhắn cho client này **/
        private void sendMessage(String msg) {
            try {
                out.println(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatServerTCP();
    }
}
