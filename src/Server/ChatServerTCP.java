import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServerTCP {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();

    public ChatServerTCP(int port) throws Exception {
        serverSocket = new ServerSocket(port);
        System.out.println("TCP Chat Server started on port " + port);

        while (true) {
            Socket client = serverSocket.accept();
            ClientHandler handler = new ClientHandler(client, this);
            clients.add(handler);
            new Thread(handler).start();
        }
    }

    public synchronized void broadcast(String message, ClientHandler sender) {
        for (ClientHandler c : clients) {
            if (c != sender) c.sendMessage(message);
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatServerTCP(6000);
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServerTCP server;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket, ChatServerTCP server) throws IOException {
        this.socket = socket;
        this.server = server;
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                server.broadcast(line, this);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}