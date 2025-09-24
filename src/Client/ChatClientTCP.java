package Client;

import java.io.*;
import java.net.*;

public class ChatClientTCP {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ChatClientTCP(String serverIP, int port) throws Exception {
        socket = new Socket(serverIP, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    // Gửi tin nhắn
    public void sendMessage(String msg) {
        out.println(msg);
    }

    // Nhận tin nhắn (blocking)
    public String receiveMessage() throws IOException {
        return in.readLine();
    }

    // Đóng kết nối
    public void close() throws IOException {
        if (socket != null) socket.close();
    }
}
