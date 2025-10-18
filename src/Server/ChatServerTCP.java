package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.concurrent.CopyOnWriteArrayList;
import Server.MyConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("JOIN:")) {
                        String user = msg.substring(5).trim();
                        this.username = user;
                        online.put(user, this);
                        // mặc định coi là đang "off" cho an toàn (client sẽ báo CAM_ON nếu có cam)
                        cameraStates.putIfAbsent(user, false);

                        // báo cho người khác: có người mới
                        broadcast("JOINED:" + user);

                        // gửi snapshot trạng thái cho người mới
                        for (Map.Entry<String, Boolean> e : cameraStates.entrySet()) {
                            String u = e.getKey();
                            boolean on = e.getValue();
                            // gửi cả chính nó (để đồng bộ label tất cả)
                            sendMessage((on ? "CAM_ON:" : "CAM_OFF:") + u);
                        }
                        continue;
                    }

                    if (msg.startsWith("CAM_ON:")) {
                        String user = msg.substring(7).trim();
                        cameraStates.put(user, true);
                        broadcast(msg);
                        continue;
                    } else if (msg.startsWith("CAM_OFF:")) {
                        String user = msg.substring(8).trim();
                        cameraStates.put(user, false);
                        broadcast(msg);
                        continue;
                    } else if (msg.startsWith("EXIT:")) {
                        handleExit(msg);     // cập nhật DB + broadcast EXIT:<username>|<roomCode>
                        cameraStates.remove(username);
                        online.remove(username);
                        break;
                    }

                    // chat normal
                    broadcast(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                clients.remove(this);
                if (username != null) {
                    online.remove(username);
                    cameraStates.remove(username);
                }
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
        // Gửi thông điệp đến các clients khác
        private void sendTo(String username, String msg) {
            for (ClientHandler c : clients) {
                if (c.getUsername().equals(username)) {
                    c.sendMessage(msg);
                    break;
                }
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

                System.out.println("Nguoi dung roi phong: " + username + " | Phong: " + roomCode);
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
