package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import Server.MyConnection;
import Utils.CryptoUtils;

public class ChatServerTCP {
    private int port = 6000;
    private CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<String, Boolean> cameraStates = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();

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
        private String username;

        public ClientHandler(Socket s) throws IOException {
            socket = s;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        public String getUsername() {
            return username;
        }

        @Override
        public void run() {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {

                    String plainMsg = tryDecrypt(msg); // Giải mã nếu là Base64
                    System.out.println("📨 Nhận từ client: " + plainMsg);

                    if (plainMsg.startsWith("JOIN:")) {
                        handleJoin(plainMsg);
                        continue;
                    }

                    if (plainMsg.startsWith("CAM_ON:")) {
                        String user = plainMsg.substring(7).trim();
                        cameraStates.put(user, true);
                        broadcast(plainMsg);
                        continue;
                    }

                    if (plainMsg.startsWith("CAM_OFF:")) {
                        String user = plainMsg.substring(8).trim();
                        cameraStates.put(user, false);
                        broadcast(plainMsg);
                        continue;
                    }

                    if (plainMsg.startsWith("EXIT:")) {
                        handleExit(plainMsg);
                        break;
                    }

                    // Nếu không phải lệnh hệ thống → tin nhắn chat
                    broadcast(plainMsg);
                }

            } catch (SocketException se) {
                System.out.println("⚠️ Client " + username + " ngắt kết nối: " + se.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }

        private void handleJoin(String msg) {
            try {
                // JOIN:<username>|<roomCode>
                String[] parts = msg.substring(5).split("\\|");
                if (parts.length < 2) return;

                String user = parts[0].trim();
                String room = parts[1].trim();
                this.username = user;

                // Kiểm tra nếu user đã ở trong cùng phòng
                for (ClientHandler c : clients) {
                    if (c != this && c.getUsername() != null && c.getUsername().equals(user)) {
                        sendMessage("ERROR:Bạn đã tham gia phòng " + room + " rồi!");
                        System.out.println("⛔ Từ chối JOIN trùng phòng: " + user);
                        return;
                    }
                }
                online.put(user, this);
                cameraStates.putIfAbsent(user, false);

                broadcast("JOINED:" + user);
                // Gửi trạng thái camera của mọi người hiện tại
                for (Map.Entry<String, Boolean> e : cameraStates.entrySet()) {
                    String u = e.getKey();
                    boolean on = e.getValue();
                    sendMessage((on ? "CAM_ON:" : "CAM_OFF:") + u);
                }

                System.out.println("✅ " + user + " tham gia phòng " + room);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handleExit(String msg) {
            try {
                // EXIT:<username>|<roomCode>
                String[] parts = msg.substring(5).split("\\|");
                if (parts.length < 2) return;

                String username = parts[0].trim();
                String roomCode = parts[1].trim();

                broadcast("EXIT:" + username + "|" + roomCode);
                updateLeaveTimeByUsername(username, roomCode);

                System.out.println("👋 " + username + " rời phòng " + roomCode);
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
                System.out.println("🕓 Đã cập nhật thời gian rời phòng cho " + username);
            } catch (Exception e) {
                System.err.println("⚠️ Lỗi cập nhật LeaveTime cho " + username);
                e.printStackTrace();
            }
        }

        /** Gửi bản rõ đến tất cả client **/
        private void broadcast(String msg) {
            for (ClientHandler c : clients) {
                try {
                    c.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        /** Gửi bản rõ đến 1 client **/
        private void sendMessage(String msg) {
            try {
                out.println(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /** Giải mã nếu là Base64 **/
        private String tryDecrypt(String msg) {
            try {
                if (isBase64(msg)) {
                    return CryptoUtils.decrypt(msg);
                }
            } catch (Exception e) {
                // Không làm gì — có thể là bản rõ
            }
            return msg;
        }

        /** Kiểm tra chuỗi có phải Base64 hợp lệ không **/
        private boolean isBase64(String s) {
            if (s == null || s.length() < 8) return false;
            return s.matches("^[A-Za-z0-9+/=\\s]+$");
        }

        private void cleanup() {
            try {
                clients.remove(this);
                if (username != null) {
                    online.remove(username);
                    cameraStates.remove(username);
                    System.out.println("🧹 Dọn dẹp client: " + username);
                }
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatServerTCP();
    }
}
