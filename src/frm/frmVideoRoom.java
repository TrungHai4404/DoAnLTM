package frm;

import Client.AudioClientUDP;
import Client.ChatClientTCP;
import Client.VideoClientUDP;
import Client.WebcamCapture;
import Utils.CryptoUtils;
import dao.ChatMessageDao;
import dao.UserDao;
import dao.VideoRoomDao;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.LinkedHashSet;
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
    private boolean videoEnabled = true;
    private boolean micEnabled = true;

    // Networking
    private VideoClientUDP videoClient;
    private ChatClientTCP chatClient;
    private AudioClientUDP audioClient;
    // ---- Camera control flags ----
    private volatile boolean capturing = false;        // luồng gửi frame chạy/đứng
    private volatile boolean isReleasingCam = false;   // chống release() 2 lần
    // trạng thái cam bên phía người khác: true=ON, false=OFF
    private final ConcurrentHashMap<String, Boolean> remoteCamOn = new ConcurrentHashMap<>();
    // UI
    private BufferedImage noCamImage;
    private WebcamCapture webcam;
    private ConcurrentHashMap<String, JLabel> videoPanels = new ConcurrentHashMap<>();
    private DefaultListModel<String> memberModel = new DefaultListModel<>();
    private VideoRoomDao roomDao = new VideoRoomDao();
    private ChatMessageDao chatDao = new ChatMessageDao();
    private UserDao userDao = new UserDao();
            

    public frmVideoRoom(String roomCode, boolean isHost, User user) { 
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.currentUser = user;
        this.localClientID = user.getUsername();
        //Cap nhat DB
        roomDao.addMember(roomCode, currentUser.getId());
        initComponents();
        txtRoomID.setText(roomCode);
        txtRoomID.setEditable(false);
        loadMembers();
        list_ThanhVien.setModel(memberModel);
        initNetworking();
        
        new javax.swing.Timer(5000, e -> loadMembers()).start();
    }
    private void initNetworking() {
        videoPanelGrid.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        noCamImage = createNoCamImage(160, 120, "Camera Off");

        boolean webcamAvailable = true;
        try {
            webcam = new WebcamCapture();
            if (!webcam.isAvailable()) webcamAvailable = false;
        } catch (Exception e) {
            webcamAvailable = false;
        }

        if (!webcamAvailable) {
            videoEnabled = false;
            btnVideo.setText("None Camera");
            btnVideo.setEnabled(false);
        }

        boolean micAvailable = isMicAvailable();
        if (!micAvailable) {
            micEnabled = false;
            btnMic.setText("None Micro");
            btnMic.setEnabled(false);
        }

        try {
            videoClient = new VideoClientUDP("192.168.1.2");
            audioClient = new AudioClientUDP("192.168.1.2");
            chatClient = new ChatClientTCP("192.168.1.2");

            // Gửi JOIN
            chatClient.sendMessage("JOIN:" + localClientID);
            chatClient.sendMessage(videoEnabled ? "CAM_ON:" + localClientID : "CAM_OFF:" + localClientID);

            audioClient.start();
            capturing = videoEnabled;
            // Gửi video
            new Thread(() -> {
                try {
                    while (true) {
                        if (!capturing || webcam == null || !videoEnabled) {
                            Thread.sleep(80);
                            continue;
                        }
                        byte[] frameData = webcam.captureFrame();
                        if (frameData != null && frameData.length > 0) {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData));
                            BufferedImage resized = resizeFrame(img, 160, 120);

                            // gửi
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(resized, "jpg", baos);
                            videoClient.sendFrame(baos.toByteArray(), localClientID);

                            // preview local
                            final BufferedImage preview = resized;
                            SwingUtilities.invokeLater(() -> updateVideoPanel(localClientID, preview));
                        }
                        Thread.sleep(33);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();


            // Thread nhận video
            new Thread(() -> {
                try {
                    byte[] buf = new byte[65536];
                    while (true) {
                        DatagramPacket pkt = videoClient.receiveFrame(buf);
                        if (pkt == null) continue;
                        byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                        if (data.length <= 36) continue;

                        String clientID = new String(Arrays.copyOfRange(data, 0, 36)).trim();

                        // Nếu phía server nói clientID đang tắt cam -> bỏ qua frame trễ
                        if (!remoteCamOn.getOrDefault(clientID, true)) {
                            continue;
                        }

                        byte[] frameBytes = Arrays.copyOfRange(data, 36, data.length);
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameBytes));
                        if (img != null)
                            SwingUtilities.invokeLater(() -> updateVideoPanel(clientID, img));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();


            // Nhận tin chat & thông điệp
            new Thread(() -> {
                try {
                    while (true) {
                        String msg = chatClient.receiveMessage();
                        if (msg == null) continue;

                        if (msg.startsWith("EXIT:")) {
                            String[] parts = msg.substring(5).split("\\|");
                            if (parts.length > 0) {
                                String exitedUser = parts[0].trim();
                                if (!exitedUser.equals(localClientID)) {   // 🔹 tránh tự xóa chính mình
                                    SwingUtilities.invokeLater(() -> {
                                        removeVideoPanel(exitedUser);
                                        removeUserFromList(exitedUser);
                                    });
                                }
                            }
                            continue;
                        }

                        if (msg.startsWith("CAM_OFF:")) {
                            String user = msg.substring(8).trim();
                            remoteCamOn.put(user, false);
                            SwingUtilities.invokeLater(() -> updateVideoPanel(user, noCamImage));
                            continue;
                        }
                        if (msg.startsWith("CAM_ON:")) {
                            String user = msg.substring(7).trim();
                            remoteCamOn.put(user, true);
                            // không cần update ngay; khung mới sẽ tự đổ về
                            continue;
                        }
                        SwingUtilities.invokeLater(() -> txt_KhungChat.append(msg + "\n"));
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

    

    // === Hàm cập nhật khung hình video ===
    private synchronized void updateVideoPanel(String clientID, BufferedImage img) {
        JLabel label = videoPanels.get(clientID);
        noCamImage = createNoCamImage(160, 120, "Camera Off");
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
        SwingUtilities.invokeLater(() -> {
            videoPanelGrid.revalidate();
            videoPanelGrid.repaint();
        });
    }
    // === Khi người dùng rời phòng ===
    private synchronized  void removeVideoPanel(String username) {
        if (!videoPanels.containsKey(username)) {
            System.out.println("⚠️ Bỏ qua, label " + username + " đã bị xóa trước đó.");
            return;
        }
        JLabel label = videoPanels.remove(username);
        if (label != null) {
             SwingUtilities.invokeLater(() -> {
                videoPanelGrid.remove(label);
                videoPanelGrid.revalidate();
                videoPanelGrid.repaint();
                System.out.println("Remove label: " + username);
            });
        }else{
            System.out.println("Khong tim thay label: "+ username);
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
    private BufferedImage resizeFrame(BufferedImage img, int width, int height) {
        Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return resized;
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
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn rời phòng không?",
                "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                capturing = false;
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                safeReleaseWebcam();
                chatClient.sendMessage("EXIT:" + localClientID + "|" + roomCode);
                audioClient.stop();
                removeVideoPanel(localClientID);
                removeUserFromList(localClientID);
                new frmMainMenu(currentUser).setVisible(true);
                dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }//GEN-LAST:event_btnEndActionPerformed

    private void btnGuiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGuiActionPerformed
        String text = txtVanBan.getText().trim();
        if (text.isEmpty()) return;
        try {
            String msg = currentUser.getFullName() + ": " + text;
            chatClient.sendMessage(msg);
            txtVanBan.setText("");
            chatDao.saveMessage(roomDao.getRoomIdByCode(roomCode), currentUser.getId(), text);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }//GEN-LAST:event_btnGuiActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        try {
            capturing = false;
            try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            safeReleaseWebcam();
            if (chatClient != null) {
                String exitMsg = "EXIT:" + localClientID+ "|" + roomCode;
                chatClient.sendMessage(exitMsg);
            }            
            noCamImage = createNoCamImage(160, 120, "Camera Off");
            updateVideoPanel(localClientID, noCamImage);
            audioClient.stop();
            SwingUtilities.invokeLater(() -> {
                removeVideoPanel(localClientID);
                removeUserFromList(localClientID);
            });
            System.out.println("Nguoi dung roi phong: " + localClientID);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }//GEN-LAST:event_formWindowClosing

    private void btnVideoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnVideoActionPerformed
        if (videoEnabled) {
            // TẮT CAM
            videoEnabled = false;
            capturing = false;           // ngừng gửi trước
            try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            safeReleaseWebcam();         // rồi mới release
            chatClient.sendMessage("CAM_OFF:" + localClientID);
            SwingUtilities.invokeLater(() -> updateVideoPanel(localClientID, null)); // vẽ "Camera Off"
            btnVideo.setText("Bật Video");
            System.out.println("🔇 Camera OFF");
        } else {
            // BẬT CAM
            if (!safeStartWebcam()) {
                JOptionPane.showMessageDialog(this, "Không thể khởi động webcam!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            videoEnabled = true;
            capturing = true;            // cho luồng gửi hoạt động lại
            chatClient.sendMessage("CAM_ON:" + localClientID);
            btnVideo.setText("Tắt Video");
            System.out.println("🎥 Camera ON");
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
    // Hàm released webcam
    private synchronized void safeReleaseWebcam() {
        if (webcam == null) return;
        if (isReleasingCam) return;
        isReleasingCam = true;
        try {
            webcam.release();
            System.out.println("✅ Webcam released safely.");
        } catch (Exception ex) {
            System.err.println("⚠️ Release webcam error: " + ex.getMessage());
        } finally {
            isReleasingCam = false;
            webcam = null;
        }
    }
    //Hàm khởi động webcam
    private synchronized boolean safeStartWebcam() {
        try {
            if (webcam != null && webcam.isAvailable()) return true;
            webcam = new WebcamCapture();
            return webcam != null && webcam.isAvailable();
        } catch (Exception e) {
            System.err.println("⚠️ Start webcam error: " + e.getMessage());
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
