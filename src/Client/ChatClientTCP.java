
package Client;
import java.io.*;
import java.net.*;

public class ChatClientTCP {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ChatClientTCP(String server, int port) throws Exception {
        socket = new Socket(server, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    public String receiveMessage() throws IOException {
        return in.readLine();
    }
}
