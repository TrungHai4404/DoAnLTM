package frm;

import Client.AudioClientUDP;
import Client.ChatClientTCP;
import Client.VideoClientUDP;
import Client.WebcamCapture;
import Network.NetworkUtils;
import dao.ChatMessageDao;
import dao.UserDao;
import dao.VideoRoomDao;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import static java.awt.SystemColor.text;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import model.User;


public class frmVideoRoom extends javax.swing.JFrame {
    private String roomCode;
    private boolean isHost;
    private User currentUser;
    private String localClientID;
    private String localClientUsername;
    // map clientID -> JLabel (video nhỏ)
    private ConcurrentHashMap<String, JLabel> videoPanels = new ConcurrentHashMap<>();
    // Khong webcame
    private BufferedImage noCamImage;
    //Danh sách thành viên
    private DefaultListModel<String> memberModel = new DefaultListModel<>();
    private ConcurrentHashMap<String, String> clientUsernames = new ConcurrentHashMap<>();
    private final VideoRoomDao roomDao = new VideoRoomDao();
    private ChatMessageDao chatDao = new ChatMessageDao();
    private UserDao userDao = new UserDao();
    // Các thuộc tính
    private VideoClientUDP videoClient;
    private ChatClientTCP chatClient;
    private AudioClientUDP audioClient;

    // Bật/tắt video, mic
    boolean webcamAvailable = true;
    
    WebcamCapture webcam = new WebcamCapture();
    private boolean videoEnabled = true;
    private boolean micEnabled = true;
            

    public frmVideoRoom(String roomCode, boolean isHost, User user) {
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.currentUser = user;
        localClientID = currentUser.getUsername();
        System.out.println(localClientID);
        initComponents();
        list_ThanhVien.setModel(memberModel);
        txtRoomID.setText(roomCode);
        
        initNetworking();
        loadMembers();
        // Cập nhật danh sách thành viên mỗi 5 giây
        new javax.swing.Timer(5000, e -> loadMembers()).start();
    }

    private void initNetworking() {
        // Kiểm tra webcam
       try {
            webcam = new WebcamCapture();
            byte[] testFrame = webcam.captureFrame();
            if (testFrame == null || testFrame.length == 0) {
                webcamAvailable = false;
            }
        } catch (Exception e) {
            webcamAvailable = false;
        }
        if (!webcamAvailable) {
            videoEnabled = false;
            System.out.println("Thiet bi khong ho tro Camera!!!");
        }
        // Kiểm tra Mic
        boolean micAvailable = isMicAvailable();
        if (!micAvailable) {
            micEnabled = false;
            System.out.println("Thiet bi khong ho tro Microphone!!!");
        }
        //Neu khong co mic va cam thi cap nhat cac nut
        if (!videoEnabled) btnVideo.setText("None Camera");
        if (!micEnabled) btnMic.setText("None Micro");
        // Cấu hình layout
        videoPanelGrid.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
         // Tạo ảnh mặc định "No Camera"
        noCamImage = createNoCamImage(160, 120, "Camera Off");
        try {
            videoClient = new VideoClientUDP("192.168.1.2");
            audioClient = new AudioClientUDP("192.168.1.2");
            chatClient = new ChatClientTCP("192.168.1.2");
            // Bắt đầu luồng audio
            audioClient.start();
            // Thread gửi video
            new Thread(() -> {
                try {
                    while (true) {
                        if (!videoEnabled) { 
                            Thread.sleep(200); 
                            continue; 
                        }
                        byte[] frameData = webcam.captureFrame();
                        if (frameData != null && frameData.length > 0) {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData));
                            BufferedImage resized = resizeFrame(img, 160, 120);
                            // Gửi frame
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(resized, "jpg", baos);
                            byte[] smallFrame = baos.toByteArray();
                            videoClient.sendFrame(smallFrame, localClientID);

                            // Cập nhật hình preview local
                            SwingUtilities.invokeLater(() -> updateVideoPanel(localClientID, resized));
                        }
                        Thread.sleep(100);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
            // Thread nhận video
            new Thread(() -> {
                try {
                    byte[] buf = new byte[65536];
                    while (true) {
                        DatagramPacket pkt = videoClient.receiveFrame(buf);
                        byte[] data = java.util.Arrays.copyOf(pkt.getData(), pkt.getLength());
                        if (data.length <= 36) continue;

                        String clientID = new String(java.util.Arrays.copyOfRange(data, 0, 36)).trim();
                        byte[] frameBytes = java.util.Arrays.copyOfRange(data, 36, data.length);
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameBytes));
                        if (img != null) {
                            SwingUtilities.invokeLater(() -> updateVideoPanel(clientID, img));
                        }
                        if (img != null) {
                            SwingUtilities.invokeLater(() -> updateVideoPanel(clientID, img));
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();

            // Thread nhận chat
            new Thread(() -> {
                try {
                    while (true) {
                        String msg = chatClient.receiveMessage();
                        if (msg != null) {
                            if (msg.startsWith("EXIT:")) {
                                try {
                                    // Dạng: EXIT:<userID>|<roomCode>
                                    String[] parts = msg.substring(5).split("\\|");
                                    if (parts.length >= 1) {
                                        String exitedUserID = parts[0].trim();
                                        SwingUtilities.invokeLater(() -> {
                                            removeVideoPanel(exitedUserID);
                                            removeUserFromList(exitedUserID);
                                        });
                                        System.out.println(exitedUserID + " da roi phong.");
                                    }
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            } else if (msg.startsWith("CAM_OFF:")) {
                                String clientID = msg.substring(8).trim();
                                SwingUtilities.invokeLater(() -> updateVideoPanel(clientID, null));
                                System.out.println(" Nguoi dung " + clientID + " da tat camera");

                            } else if (msg.startsWith("CAM_ON:")) {
                                String clientID = msg.substring(7).trim();
                                System.out.println(" Nguoi dung " + clientID + " da bat camera");
                                // Không cần làm gì thêm — video sẽ tự hiển thị khi frame mới đến

                            } else {
                                SwingUtilities.invokeLater(() -> txt_KhungChat.append(msg + "\n"));
                            }
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
    private void loadMembers() {
        Set<String> uniqueMembers = new LinkedHashSet<>(roomDao.getMembersByRoomCode(roomCode));
        memberModel.clear();
        for (String name : uniqueMembers) {
            memberModel.addElement(name);
        }
        list_ThanhVien.setModel(memberModel);
    }   
    private void removeUserFromList(String fullName) {
        for (int i = 0; i < memberModel.size(); i++) {
            if (memberModel.get(i).equals(fullName)) {
                memberModel.remove(i);
                break;
            }
        }
        list_ThanhVien.setModel(memberModel);
    }

    private BufferedImage resizeFrame(BufferedImage img, int width, int height) {
        Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    // === Hàm cập nhật khung hình video ===
    private void updateVideoPanel(String clientID, BufferedImage img) {
        JLabel label = videoPanels.get(clientID);

        if (label == null) {
            // 🧩 Tạo JLabel mới cho client
            label = new JLabel("Đang kết nối...", SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(160, 120));
            label.setOpaque(true);
            label.setBackground(Color.BLACK);
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Arial", Font.PLAIN, 12));

            videoPanels.put(clientID, label);
            videoPanelGrid.add(label);
        }
        
        // 🔄 Cập nhật ảnh
        if (img == null) {
            label.setIcon(new ImageIcon(noCamImage));
            label.setText("Camera Off");
        } else {
            label.setIcon(new ImageIcon(img));
            label.setText(null);
        }

        // 🧱 Làm mới layout
        videoPanelGrid.revalidate();
        videoPanelGrid.repaint();
    }
    // === Khi người dùng tắt cam ===
    private void handleToggleCamera() {
        videoEnabled = !videoEnabled;
        if (!videoEnabled) {
            // Hiển thị hình ảnh tắt cam
            updateVideoPanel(localClientID, null);
        }
    }
    // === Khi người dùng rời phòng ===
    private void removeVideoPanel(String clientID) {
        JLabel label = videoPanels.remove(clientID);
        if (label != null) {
            videoPanelGrid.remove(label);
            videoPanelGrid.revalidate();
            videoPanelGrid.repaint();
            System.out.println("Remove label " + clientID);
        }
    }
    // === Hàm tạo ảnh "Camera Off" ===
    private BufferedImage createNoCamImage(int width, int height, String text) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));

        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        int y = (height - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
        g.dispose();
        return img;
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
        jLabel3 = new javax.swing.JLabel();
        txtRoomID = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
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
        getContentPane().add(txtVanBan, new org.netbeans.lib.awtextra.AbsoluteConstraints(620, 460, 120, 40));

        btnGui.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        btnGui.setText("Gửi");
        btnGui.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGuiActionPerformed(evt);
            }
        });
        getContentPane().add(btnGui, new org.netbeans.lib.awtextra.AbsoluteConstraints(740, 460, 50, 40));

        jLabel1.setText("Danh sách TV");
        getContentPane().add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(665, 19, 124, -1));

        jLabel2.setText("Khung chat");
        getContentPane().add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(674, 237, 71, -1));

        btnMic.setText("Tắt Mic");
        btnMic.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMicActionPerformed(evt);
            }
        });
        getContentPane().add(btnMic, new org.netbeans.lib.awtextra.AbsoluteConstraints(185, 489, -1, -1));

        btnVideo.setText("Tắt Video");
        btnVideo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnVideoActionPerformed(evt);
            }
        });
        getContentPane().add(btnVideo, new org.netbeans.lib.awtextra.AbsoluteConstraints(340, 490, -1, -1));

        btnEnd.setText("Kết thúc");
        btnEnd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEndActionPerformed(evt);
            }
        });
        getContentPane().add(btnEnd, new org.netbeans.lib.awtextra.AbsoluteConstraints(522, 489, -1, -1));

        jLabel3.setText("RoomID");
        getContentPane().add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 470, -1, -1));
        getContentPane().add(txtRoomID, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 490, 110, -1));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnEndActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEndActionPerformed
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn rời phòng không?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // Gửi tin EXIT:<userID>|<roomCode> tới server
                if (chatClient != null) {
                    String exitMsg = "EXIT:" + localClientID + "|" + roomCode;
                    chatClient.sendMessage(exitMsg);
                }
                // Giải phóng webcam và audio
                if (audioClient != null) audioClient.stop();
                if (webcam != null) webcam.release();
                // Xóa video panel local
                SwingUtilities.invokeLater(() -> removeVideoPanel(localClientID));
                // Quay lại menu chính
                new frmMainMenu(currentUser).setVisible(true);
                this.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }//GEN-LAST:event_btnEndActionPerformed

    private void btnGuiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGuiActionPerformed
        String text = txtVanBan.getText().trim();
            if (!text.isEmpty()) {
                try {
                    chatClient.sendMessage(currentUser.getFullName() + ": " + text);
                    txtVanBan.setText("");
                    chatDao.saveMessage(roomDao.getRoomIdByCode(roomCode), currentUser.getId(), text);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
    }//GEN-LAST:event_btnGuiActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        try {
            if (chatClient != null) {
                String exitMsg = "EXIT:" + localClientID+ "|" + roomCode;
                chatClient.sendMessage(exitMsg);
            }
            if (audioClient != null) audioClient.stop();
            if (webcam != null) webcam.release();
            SwingUtilities.invokeLater(() -> {
                removeVideoPanel(localClientID);
                removeUserFromList(localClientID);
            });
            System.out.println("Nguoi dung roi phong: " + localClientUsername);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }//GEN-LAST:event_formWindowClosing

    private void btnVideoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnVideoActionPerformed
        videoEnabled = !videoEnabled;
        String clientID = localClientID;
        JLabel label = videoPanels.get(clientID);
        if (videoEnabled) {
            btnVideo.setText("Tắt Video");
            System.out.println("Camera On");

            // Gửi thông báo bật cam tới các client khác
            chatClient.sendMessage("CAM_ON:" + clientID);

            // Bật cam lại cho chính mình
            try {
                webcam = new WebcamCapture();
                byte[] frameData = webcam.captureFrame();
                if (frameData != null) {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData));
                    BufferedImage resized = resizeFrame(img, 160, 120);
                    updateVideoPanel(clientID, resized);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            btnVideo.setText("Bật Video");
            System.out.println("Camera off");

            // Gửi thông báo tắt cam tới các client khác
            chatClient.sendMessage("CAM_OFF:" + clientID);

            updateVideoPanel(clientID, null);

            
            videoPanelGrid.revalidate();
            videoPanelGrid.repaint();
            if (webcam != null) {
                webcam.release();
                System.out.println("Webcam released.");
            }
        }
        
    }//GEN-LAST:event_btnVideoActionPerformed

    private void btnMicActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMicActionPerformed
        if (audioClient != null) {
            boolean isMicNowEnabled = audioClient.toggleMic();

            if (isMicNowEnabled) {
                btnMic.setText("Tắt Mic");
            } else {
                btnMic.setText("Bật Mic");
            }
        }
    }//GEN-LAST:event_btnMicActionPerformed
    // Hàm kiểm tra mic
    private boolean isMicAvailable() {
        try {
            AudioFormat format = new AudioFormat(44100.0F, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return false;
            }
            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(format);
            mic.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnEnd;
    private javax.swing.JButton btnGui;
    private javax.swing.JButton btnMic;
    private javax.swing.JButton btnVideo;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> list_ThanhVien;
    private javax.swing.JTextField txtRoomID;
    private javax.swing.JTextField txtVanBan;
    private javax.swing.JTextArea txt_KhungChat;
    private javax.swing.JPanel videoPanelGrid;
    // End of variables declaration//GEN-END:variables
}
