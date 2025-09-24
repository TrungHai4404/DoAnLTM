package Client;

import java.net.*;
import java.util.Arrays;

// VideoClientUDP xử lý gửi/nhận frame kèm clientID
public class VideoClientUDP {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String localClientID;

    public VideoClientUDP(String serverIP, String clientID) throws Exception {
        socket = new DatagramSocket();
        serverAddress = InetAddress.getByName(serverIP);
        serverPort = 5000; // port server UDP
        this.localClientID = clientID;
    }

    // Class lưu frame + clientID
    public static class FrameData {
        public String clientID;
        public byte[] data;

        public FrameData(String clientID, byte[] data) {
            this.clientID = clientID;
            this.data = data;
        }
    }

    // Gửi frame kèm clientID
    public void sendFrame(byte[] data, String clientID) throws Exception {
        byte[] idBytes = clientID.getBytes("UTF-8");
        byte[] payload = new byte[4 + idBytes.length + data.length];

        int idLen = idBytes.length;
        payload[0] = (byte) (idLen >> 24);
        payload[1] = (byte) (idLen >> 16);
        payload[2] = (byte) (idLen >> 8);
        payload[3] = (byte) (idLen);

        System.arraycopy(idBytes, 0, payload, 4, idLen);
        System.arraycopy(data, 0, payload, 4 + idLen, data.length);

        DatagramPacket packet = new DatagramPacket(payload, payload.length, serverAddress, serverPort);
        socket.send(packet);
    }

    // Nhận frame từ server
    public FrameData receiveFrame() throws Exception {
        byte[] buf = new byte[65507]; // max UDP packet size
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        byte[] received = Arrays.copyOf(packet.getData(), packet.getLength());

        // Parse clientID
        int idLen = ((received[0] & 0xFF) << 24) | ((received[1] & 0xFF) << 16)
                  | ((received[2] & 0xFF) << 8) | (received[3] & 0xFF);
        String clientID = new String(received, 4, idLen, "UTF-8");
        byte[] data = Arrays.copyOfRange(received, 4 + idLen, received.length);

        return new FrameData(clientID, data);
    }

    public void close() {
        if (socket != null) socket.close();
    }
}
