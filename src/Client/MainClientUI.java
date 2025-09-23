package Client;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

public class MainClientUI {
    JFrame frame = new JFrame("Video Call Room");
    JPanel videoGrid = new JPanel();
    JTextArea chatArea = new JTextArea();
    JTextField chatInput = new JTextField();

    VideoClientUDP videoClient;
    ChatClientTCP chatClient;

    public MainClientUI(String serverIP) throws Exception {
        frame.setLayout(new BorderLayout());

        videoGrid.setLayout(new GridLayout(2,2)); // Grid hiển thị nhiều video
        frame.add(videoGrid, BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatArea.setEditable(false);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        chatPanel.add(chatInput, BorderLayout.SOUTH);
        frame.add(chatPanel, BorderLayout.EAST);

        frame.setSize(1200, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Khởi tạo client
        videoClient = new VideoClientUDP(serverIP);
        chatClient = new ChatClientTCP(serverIP, 6000);

        // Thread nhận chat
        new Thread(() -> {
            try {
                while (true) {
                    String msg = chatClient.receiveMessage();
                    SwingUtilities.invokeLater(() -> chatArea.append(msg + "\n"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        // Thread gửi video
        new Thread(() -> {
            WebcamCapture webcam = new WebcamCapture();
            try {
                while (true) {
                    byte[] frameData = webcam.captureFrame();
                    if (frameData != null) {
                        videoClient.sendFrame(frameData);
                    }
                    Thread.sleep(100); // ~10 FPS
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                webcam.release();
            }
        }).start();
        // TODO: Thread gửi & nhận video (demo có thể dùng ảnh dummy trước)
    }

    public static void main(String[] args) throws Exception {
        new MainClientUI("192.168.1.100"); // đổi thành IP server của bạn
    }
}
