package Client;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

public class MainClientUI {
    private JFrame frame = new JFrame("Video Call Room");
    private JPanel videoGrid = new JPanel();
    private JTextArea chatArea = new JTextArea();
    private JTextField chatInput = new JTextField();

    private VideoClientUDP videoClient;
    private ChatClientTCP chatClient;

    // Map clientID -> JLabel
    private ConcurrentHashMap<String, JLabel> videoPanels = new ConcurrentHashMap<>();

    private String localClientID = String.valueOf(System.currentTimeMillis()); // ID tạm thời

    public MainClientUI(String serverIP) throws Exception {
        initGUI();

        videoClient = new VideoClientUDP(serverIP, localClientID); // localClientID gửi kèm
        chatClient = new ChatClientTCP(serverIP, 6000);

        // Thread nhận chat
        new Thread(() -> {
            try {
                while (true) {
                    String msg = chatClient.receiveMessage();
                    if (msg != null) {
                        SwingUtilities.invokeLater(() -> chatArea.append(msg + "\n"));
                    }
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
                    if (frameData != null && frameData.length > 0) {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData));
                        if (img != null) {
                            // Resize & JPEG
                            Image scaled = img.getScaledInstance(320, 240, Image.SCALE_SMOOTH);
                            BufferedImage resized = new BufferedImage(320, 240, BufferedImage.TYPE_3BYTE_BGR);
                            Graphics2D g2d = resized.createGraphics();
                            g2d.drawImage(scaled, 0, 0, null);
                            g2d.dispose();

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(resized, "jpg", baos);
                            byte[] smallFrame = baos.toByteArray();

                            // Gửi frame kèm clientID
                            videoClient.sendFrame(smallFrame, localClientID);

                            // Hiển thị video của chính mình
                            SwingUtilities.invokeLater(() -> updateVideoPanel(localClientID, resized));
                        }
                    }
                    Thread.sleep(100); // 10 FPS
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                webcam.release();
            }
        }).start();

        // Thread nhận video từ các client khác
        new Thread(() -> {
            try {
                while (true) {
                    // receiveFrame trả về Pair<clientID, byte[]>
                    VideoClientUDP.FrameData frameData = videoClient.receiveFrame();
                    if (frameData != null && frameData.data.length > 0) {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData.data));
                        if (img != null) {
                            SwingUtilities.invokeLater(() -> updateVideoPanel(frameData.clientID, img));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Cập nhật video panel, nếu client mới tạo JLabel mới
    private void updateVideoPanel(String clientID, BufferedImage img) {
        JLabel label = videoPanels.get(clientID);
        if (label == null) {
            label = new JLabel();
            label.setPreferredSize(new Dimension(320, 240));
            label.setOpaque(true);
            label.setBackground(Color.BLACK);
            videoPanels.put(clientID, label);
            videoGrid.add(label);
            videoGrid.revalidate();
            videoGrid.repaint();
        }
        label.setIcon(new ImageIcon(img));
    }

    private void initGUI() {
        frame.setLayout(new BorderLayout());

        videoGrid.setLayout(new GridLayout(2, 2, 5, 5)); // margin nhỏ
        frame.add(videoGrid, BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatArea.setEditable(false);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        chatPanel.add(chatInput, BorderLayout.SOUTH);
        chatPanel.setPreferredSize(new Dimension(300, 800));
        frame.add(chatPanel, BorderLayout.EAST);

        frame.setSize(1200, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new MainClientUI("192.168.1.5"); // IP server
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
