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

    // ✅ Chỉ mã hóa tin nhắn "chat" thực sự
    public void sendMessage(String msg) {
        try {
            // Nếu là lệnh hệ thống, gửi dạng rõ
            if (isSystemCommand(msg)) {
                out.println(msg);
            } else {
                // Còn nếu là tin nhắn chat của người dùng → mã hóa
                String encrypted = CryptoUtils.encrypt(msg);
                out.println(encrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ✅ Nhận tin (server gửi dạng rõ)
    public String receiveMessage() {
        try {
            String msg = in.readLine();
            return msg; // server gửi bản rõ, không cần decrypt
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void close() throws IOException {
        socket.close();
    }

    // ✅ Hàm xác định tin hệ thống
    private boolean isSystemCommand(String msg) {
        return msg.startsWith("JOIN:")
            || msg.startsWith("EXIT:")
            || msg.startsWith("CAM_ON:")
            || msg.startsWith("CAM_OFF:")
            || msg.startsWith("ERROR:")
            || msg.startsWith("JOINED:");
    }
}
