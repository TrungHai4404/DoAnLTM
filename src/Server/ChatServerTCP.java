package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.concurrent.CopyOnWriteArrayList;
import Server.MyConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import Utils.CryptoUtils;

public class ChatServerTCP {
    private int port = 6000;
    private CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<String, Boolean> cameraStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();

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
                String encryptedMsg;

                while ((encryptedMsg = in.readLine()) != null) {
                    String decrypted = null;
                    try {
                        decrypted = CryptoUtils.decrypt(encryptedMsg);
                    } catch (Exception e) {
                        System.err.println("⚠️ Lỗi giải mã tin nhắn!");
                        continue;
                    }

                    System.out.println("📩 Nhận từ client (" + username + "): " + decrypted);

                    // ==== 1. Xử lý logic tin nhắn gốc ====
                    if (decrypted.startsWith("JOIN:")) {
                        username = decrypted.substring(5).trim();
                        online.put(username, this);
                        cameraStates.putIfAbsent(username, false);

                        broadcastEncrypted("JOINED:" + username);

                        // Gửi snapshot trạng thái camera cho người mới
                        for (Map.Entry<String, Boolean> e : cameraStates.entrySet()) {
                            String u = e.getKey();
                            boolean on = e.getValue();
                            sendEncrypted((on ? "CAM_ON:" : "CAM_OFF:") + u);
                        }
                        continue;
                    }

                    if (decrypted.startsWith("CAM_ON:")) {
                        String user = decrypted.substring(7).trim();
                        cameraStates.put(user, true);
                        broadcastEncrypted(decrypted);
                        continue;
                    }

                    if (decrypted.startsWith("CAM_OFF:")) {
                        String user = decrypted.substring(8).trim();
                        cameraStates.put(user, false);
                        broadcastEncrypted(decrypted);
                        continue;
                    }

                    if (decrypted.startsWith("EXIT:")) {
                        handleExit(decrypted);
                        cameraStates.remove(username);
                        online.remove(username);
                        break;
                    }

                    // 🔁 Gửi tin chat thường
                    broadcastEncrypted(decrypted);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                clients.remove(this);
                if (username != null) {
                    online.remove(username);
                    cameraStates.remove(username);
                }
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }

        // === 🔐 Gửi mã hóa ===
        private void sendEncrypted(String plainMsg) {
            try {
                String encrypted = CryptoUtils.encrypt(plainMsg);
                out.println(encrypted);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // === 🔐 Broadcast mã hóa ===
        private void broadcastEncrypted(String plainMsg) {
            for (ClientHandler c : clients) {
                c.sendEncrypted(plainMsg);
            }
        }

        private void handleExit(String plainMsg) {
            try {
                // EXIT:<username>|<roomCode>
                String[] parts = plainMsg.substring(5).split("\\|");
                if (parts.length < 2) return;
                String username = parts[0].trim();
                String roomCode = parts[1].trim();

                broadcastEncrypted("EXIT:" + username + "|" + roomCode);
                updateLeaveTimeByUsername(username, roomCode);

                System.out.println("👋 " + username + " Da roi phong " + roomCode);
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
                System.out.println("Da cap nhat thoi gian roi phong: " + username);
            } catch (Exception e) {
                System.err.println("Loi cap nhat thoi gian roi phong: " + username);
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatServerTCP();
    }
}
