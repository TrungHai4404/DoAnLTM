package Client;

import java.io.*;
import java.net.*;

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
        out.println(msg);
    }

    public String receiveMessage() throws IOException {
        return in.readLine();
    }
}
