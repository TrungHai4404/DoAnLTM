package frm;

import Client.ChatClientTCP;
import Client.VideoClientUDP;
import Client.WebcamCapture;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;


public class frmVideoRoom extends javax.swing.JFrame {
    private String roomID;
    private boolean isHost;
    private String localClientID = String.valueOf(System.currentTimeMillis());

    // map clientID -> JLabel (video nhỏ)
    private ConcurrentHashMap<String, JLabel> videoPanels = new ConcurrentHashMap<>();
    private DefaultListModel<String> memberModel = new DefaultListModel<>();

    private VideoClientUDP videoClient;
    private ChatClientTCP chatClient;

    // Bật/tắt video, mic
    private boolean videoEnabled = true;
    private boolean micEnabled = true;
    
    public frmVideoRoom(String roomID, boolean isHost) {
        this.roomID = roomID;
        this.isHost = isHost;
        initComponents();
        list_ThanhVien.setModel(memberModel);

        videoPanelGrid.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        initNetworking();
        initActions();
    }
    // Constructor mặc định test (không cần truyền roomID)
    public frmVideoRoom() {
        this("ROOM_TEST", true);
    }

    private void initNetworking() {
        try {
            videoClient = new VideoClientUDP("192.168.1.5", localClientID);
            chatClient = new ChatClientTCP("192.168.1.5", 6000);

            addMember(localClientID);

            // Thread nhận video
            new Thread(() -> {
                try {
                    while (true) {
                        VideoClientUDP.FrameData fd = videoClient.receiveFrame();
                        if (fd != null && fd.data.length > 0) {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(fd.data));
                            if (img != null) {
                                SwingUtilities.invokeLater(() -> updateVideoPanel(fd.clientID, img));
                                addMember(fd.clientID);
                            }
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
                        if (!videoEnabled) {
                            Thread.sleep(100);
                            continue;
                        }
                        byte[] frameData = webcam.captureFrame();
                        if (frameData != null && frameData.length > 0) {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData));
                            if (img != null) {
                                BufferedImage resized = resizeFrame(img, 160, 120); // video nhỏ
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(resized, "jpg", baos);
                                byte[] smallFrame = baos.toByteArray();
                                videoClient.sendFrame(smallFrame, localClientID);
                                SwingUtilities.invokeLater(() -> updateVideoPanel(localClientID, resized));
                            }
                        }
                        Thread.sleep(100); // ~10 FPS
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    webcam.release();
                }
            }).start();

            // Thread nhận chat
            new Thread(() -> {
                try {
                    while (true) {
                        String msg = chatClient.receiveMessage();
                        if (msg != null) {
                            SwingUtilities.invokeLater(() -> txt_KhungChat.append(msg + "\n"));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initActions() {
        btnGui.addActionListener(e -> {
            String text = txtVanBan.getText().trim();
            if (!text.isEmpty()) {
                try {
                    chatClient.sendMessage(localClientID + ": " + text);
                    txtVanBan.setText("");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        btnEnd.addActionListener(e -> dispose());

        btnVideo.addActionListener(e -> {
            videoEnabled = !videoEnabled;
            btnVideo.setText(videoEnabled ? "Tắt Video" : "Bật Video");
        });

        btnMic.addActionListener(e -> {
            micEnabled = !micEnabled;
            btnMic.setText(micEnabled ? "Tắt Mic" : "Bật Mic");
        });
    }

    private BufferedImage resizeFrame(BufferedImage img, int width, int height) {
        Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    private void updateVideoPanel(String clientID, BufferedImage img) {
        JLabel label = videoPanels.get(clientID);
        if (label == null) {
            // Tạo JLabel mới cho client mới
            label = new JLabel();
            label.setPreferredSize(new Dimension(160, 120));
            label.setOpaque(true);
            label.setBackground(Color.WHITE);

            videoPanels.put(clientID, label);
            videoPanelGrid.add(label); // Thêm theo thứ tự clientID

            // Cập nhật lại layout
            videoPanelGrid.revalidate();
            videoPanelGrid.repaint();
        }

        // Cập nhật hình ảnh
        label.setIcon(new ImageIcon(img));
    }
    private void removeVideoPanel(String clientID) {
        JLabel label = videoPanels.remove(clientID);
        if (label != null) {
            videoPanelGrid.remove(label);
            videoPanelGrid.revalidate();
            videoPanelGrid.repaint();
        }
        memberModel.removeElement(clientID);
    }

    private void addMember(String clientID) {
        if (!memberModel.contains(clientID)) {
            memberModel.addElement(clientID);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new frmVideoRoom().setVisible(true); // test LAN
        });
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        videoPanelGrid = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        list_ThanhVien = new javax.swing.JList<>();
        jScrollPane2 = new javax.swing.JScrollPane();
        txt_KhungChat = new javax.swing.JTextArea();
        txtVanBan = new javax.swing.JTextField();
        btnGui = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        btnMic = new javax.swing.JButton();
        btnVideo = new javax.swing.JButton();
        btnEnd = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(800, 500));
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        videoPanelGrid.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        videoPanelGrid.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        videoPanelGrid.setLayout(new java.awt.GridLayout(1, 0));
        getContentPane().add(videoPanelGrid, new org.netbeans.lib.awtextra.AbsoluteConstraints(3, 35, 592, 419));

        jScrollPane1.setViewportView(list_ThanhVien);

        getContentPane().add(jScrollPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(618, 41, 171, 190));

        txt_KhungChat.setColumns(20);
        txt_KhungChat.setRows(5);
        jScrollPane2.setViewportView(txt_KhungChat);

        getContentPane().add(jScrollPane2, new org.netbeans.lib.awtextra.AbsoluteConstraints(620, 260, 171, 199));
        getContentPane().add(txtVanBan, new org.netbeans.lib.awtextra.AbsoluteConstraints(618, 460, 119, -1));

        btnGui.setText("->");
        getContentPane().add(btnGui, new org.netbeans.lib.awtextra.AbsoluteConstraints(743, 460, 46, -1));

        jLabel1.setText("Danh sách TV");
        getContentPane().add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(665, 19, 124, -1));

        jLabel2.setText("Khung chat");
        getContentPane().add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(674, 237, 71, -1));

        btnMic.setText("Mic");
        getContentPane().add(btnMic, new org.netbeans.lib.awtextra.AbsoluteConstraints(185, 489, -1, -1));

        btnVideo.setText("Video");
        getContentPane().add(btnVideo, new org.netbeans.lib.awtextra.AbsoluteConstraints(359, 489, -1, -1));

        btnEnd.setText("Kết thúc");
        getContentPane().add(btnEnd, new org.netbeans.lib.awtextra.AbsoluteConstraints(522, 489, -1, -1));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnEnd;
    private javax.swing.JButton btnGui;
    private javax.swing.JButton btnMic;
    private javax.swing.JButton btnVideo;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> list_ThanhVien;
    private javax.swing.JTextField txtVanBan;
    private javax.swing.JTextArea txt_KhungChat;
    private javax.swing.JPanel videoPanelGrid;
    // End of variables declaration//GEN-END:variables
}
