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

    public void sendMessage(String msg) {
        try {
            String encrypted = CryptoUtils.encrypt(msg);
            out.println(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String receiveMessage() {
        try {
            String encrypted = in.readLine();
            if (encrypted != null) {
                return CryptoUtils.decrypt(encrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public void close() throws IOException {
        socket.close();
    }
}
