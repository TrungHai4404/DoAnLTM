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
    public String receiveMessage() {
        try {
            String msg = in.readLine();
            if (msg == null) return null;

            if (msg.startsWith("JOIN:")
                || msg.startsWith("EXIT:")
                || msg.startsWith("CAM_ON:")
                || msg.startsWith("CAM_OFF:")
                || msg.startsWith("JOINED:")
                || msg.startsWith("ERROR:")) {
                return msg;
            }
            return CryptoUtils.decrypt(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void close() throws IOException {
        socket.close();
    }
}
