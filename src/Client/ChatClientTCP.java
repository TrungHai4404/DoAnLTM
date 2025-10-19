package Client;

import java.io.*;
import java.net.*;
import Utils.CryptoUtils;

public class ChatClientTCP {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private int port = 6000;

    public ChatClientTCP(String serverIP) throws Exception {
        socket = new Socket(serverIP, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    // Gửi tin nhắn (mã hóa nếu là chat)
    public void sendMessage(String msg) {
        try {
            if (msg.startsWith("JOIN:")
                || msg.startsWith("EXIT:")
                || msg.startsWith("CAM_ON:")
                || msg.startsWith("CAM_OFF:")
                || msg.startsWith("JOINED:")
                || msg.startsWith("ERROR:")) {
                out.println(msg);
            } else {
                String encrypted = CryptoUtils.encrypt(msg);
                out.println(encrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Nhận tin nhắn (giải mã nếu cần)
    public String receiveMessage() throws IOException {
        try {
            String msg = in.readLine();

            // Nếu server đóng luồng (null)
            if (msg == null) {
                throw new SocketException("Server closed connection");
            }

            // Các thông điệp hệ thống (không mã hóa)
            if (msg.startsWith("JOIN:")
                    || msg.startsWith("EXIT:")
                    || msg.startsWith("CAM_ON:")
                    || msg.startsWith("CAM_OFF:")
                    || msg.startsWith("JOINED:")
                    || msg.startsWith("ERROR:")) {
                return msg;
            }

            // Các tin nhắn chat bình thường → giải mã
            return CryptoUtils.decrypt(msg);

        } catch (SocketException e) {
            // Server đã ngắt kết nối TCP
            throw new IOException("CHAT_SERVER_DISCONNECTED", e);
        } catch (IOException e) {
            throw e; // cho thread phía trên xử lý
        } catch (Exception e) {
            System.err.println("❌ Lỗi nhận tin nhắn TCP: " + e.getMessage());
            return null;
        }
    }


    public void close() throws IOException {
        socket.close();
    }
}
