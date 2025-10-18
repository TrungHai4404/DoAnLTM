package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import Server.MyConnection;
import Utils.CryptoUtils;

public class ChatServerTCP {
    private int port = 6000;
    private CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<String, Boolean> cameraStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<String>> userRooms = new ConcurrentHashMap<>();

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
                    String decrypted;
                    try {
                        decrypted = CryptoUtils.decrypt(encryptedMsg.trim());
                    } catch (Exception e) {
                        System.err.println("⚠️ Lỗi giải mã tin nhắn!");
                        continue;
                    }

                    // ====== JOIN ======
                    if (decrypted.startsWith("JOIN:")) {
                        String[] parts = decrypted.substring(5).split("\\|");
                        if (parts.length < 2) continue;

                        username = parts[0].trim();
                        String roomCode = parts[1].trim();

                        userRooms.putIfAbsent(username, ConcurrentHashMap.newKeySet());
                        Set<String> rooms = userRooms.get(username);

                        if (rooms.contains(roomCode)) {
                            sendEncrypted("ERROR:Bạn đã tham gia phòng này rồi!");
                            System.out.println("⚠️ " + username + " cố vào lại phòng " + roomCode);
                            continue;
                        }

                        rooms.add(roomCode);
                        online.put(username, this);
                        cameraStates.putIfAbsent(username, false);

                        broadcastEncrypted("JOINED:" + username + "|" + roomCode);
                        System.out.println("✅ " + username + " tham gia phòng " + roomCode);

                        // Gửi lại trạng thái camera hiện có
                        for (Map.Entry<String, Boolean> e : cameraStates.entrySet()) {
                            sendEncrypted((e.getValue() ? "CAM_ON:" : "CAM_OFF:") + e.getKey());
                        }
                        continue;
                    }

                    // ====== CAMERA ======
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

                    // ====== EXIT ======
                    if (decrypted.startsWith("EXIT:")) {
                        handleExit(decrypted);
                        break;
                    }

                    // ====== Tin nhắn thường ======
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

        // Gửi mã hóa
        private void sendEncrypted(String plainMsg) {
            try {
                out.println(CryptoUtils.encrypt(plainMsg));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Broadcast mã hóa
        private void broadcastEncrypted(String plainMsg) {
            for (ClientHandler c : clients) {
                c.sendEncrypted(plainMsg);
            }
        }

        // Xử lý khi rời phòng
        private void handleExit(String msg) {
            try {
                // EXIT:<username>|<roomCode>
                String[] parts = msg.substring(5).split("\\|");
                if (parts.length < 2) return;

                String username = parts[0].trim();
                String roomCode = parts[1].trim();

                // Xóa phòng khỏi danh sách userRooms
                if (userRooms.containsKey(username)) {
                    userRooms.get(username).remove(roomCode);
                    if (userRooms.get(username).isEmpty()) {
                        userRooms.remove(username);
                    }
                }

                broadcastEncrypted("EXIT:" + username + "|" + roomCode);
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
                System.out.println("🕒 Cập nhật thời gian rời phòng: " + username);
            } catch (Exception e) {
                System.err.println("❌ Lỗi cập nhật thời gian rời phòng: " + username);
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatServerTCP();
    }
}
