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
    private CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<String, Boolean> cameraStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();
    private VideoRoomDao roomDao = new VideoRoomDao();
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
                            System.err.println("⚠️ Không thể giải mã tin nhắn: " + msg);
                            continue;
                        }
                    }

                    System.out.println("📩 Nhận từ client (" + username + "): " + decrypted);

                    if (decrypted.startsWith("JOIN:")) {
                        username = decrypted.substring(5).trim();
                        online.put(username, this);
                        cameraStates.putIfAbsent(username, false);
                        broadcastEncrypted("JOINED:" + username);
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

                    // Chat thường → broadcast có mã hóa
                    broadcastEncrypted(decrypted);
                }

            } catch(SocketException se){
                System.err.println(" Client " + username + " disconected: " + se.getMessage());
            }catch (Exception e) {
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

        // Broadcast cho tất cả client
        private void broadcastEncrypted(String plainMsg) {
            for (ClientHandler c : clients) {
                c.sendEncrypted(plainMsg);
            }
        }

        private void handleExit(String plainMsg) {
            try {
                String[] parts = plainMsg.substring(5).split("\\|");
                if (parts.length < 2) return;
                String username = parts[0].trim();
                String roomCode = parts[1].trim();

                broadcastEncrypted("EXIT:" + username + "|" + roomCode);
                roomDao.markLeave(username, roomCode);
                System.out.println(username + " đã rời phòng " + roomCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    
    }

    public static void main(String[] args) throws Exception {
        new ChatServerTCP();
    }
}
