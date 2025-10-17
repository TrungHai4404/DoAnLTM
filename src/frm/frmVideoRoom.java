package frm;

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
import java.awt.Graphics2D;
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
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import model.User;


public class frmVideoRoom extends javax.swing.JFrame {
    private String roomCode;
    private boolean isHost;
    private User currentUser;
    private String localClientID;
    // map clientID -> JLabel (video nhỏ)
    private ConcurrentHashMap<String, JLabel> videoPanels = new ConcurrentHashMap<>();
    private DefaultListModel<String> memberModel = new DefaultListModel<>();
    private ConcurrentHashMap<String, String> clientUsernames = new ConcurrentHashMap<>();
    private final VideoRoomDao roomDao = new VideoRoomDao();
    private ChatMessageDao chatDao = new ChatMessageDao();
    private UserDao userDao = new UserDao();
    private VideoClientUDP videoClient;
    private ChatClientTCP chatClient;

    // Bật/tắt video, mic
    private boolean videoEnabled = true;
    private boolean micEnabled = true;
            

    public frmVideoRoom(String roomCode, boolean isHost, User user) {
        
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.currentUser = user;
        initComponents();
        list_ThanhVien.setModel(memberModel);
        txtRoomID.setText(roomCode);
        videoPanelGrid.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        initNetworking();
        initActions();
        loadMembers();
        // Cập nhật danh sách thành viên mỗi 5 giây
        new javax.swing.Timer(5000, e -> loadMembers()).start();

    }
    // Constructor mặc định test (không cần truyền roomID)

    
    private void initNetworking() {
        localClientID = currentUser.getId();
        try {
            videoClient = new VideoClientUDP("192.168.1.2");
            chatClient = new ChatClientTCP("192.168.1.2", 6000);

            WebcamCapture webcam = new WebcamCapture();

            // Thread gửi video
            new Thread(() -> {
                try {
                    while (true) {
                        if (!videoEnabled) { Thread.sleep(100); continue; }
                        byte[] frameData = webcam.captureFrame();
                        if (frameData != null && frameData.length > 0) {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData));
                            BufferedImage resized = resizeFrame(img, 160, 120);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(resized, "jpg", baos);
                            byte[] smallFrame = baos.toByteArray();

                            videoClient.sendFrame(smallFrame, localClientID);
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
                                String clientID = msg.substring(5);
                            } else {
                                SwingUtilities.invokeLater(() -> txt_KhungChat.append(msg + "\n"));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            // Release webcam khi đóng form
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    try {
                        if (chatClient != null) {
                            webcam.release();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }


    private void initActions() {
        btnGui.addActionListener(e -> {
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
        });
        btnVideo.addActionListener(e -> {
            videoEnabled = !videoEnabled;
            btnVideo.setText(videoEnabled ? "Tắt Video" : "Bật Video");
        });

        btnMic.addActionListener(e -> {
            micEnabled = !micEnabled;
            btnMic.setText(micEnabled ? "Tắt Mic" : "Bật Mic");
        });
        
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

        btnMic.setText("Mic");
        getContentPane().add(btnMic, new org.netbeans.lib.awtextra.AbsoluteConstraints(185, 489, -1, -1));

        btnVideo.setText("Video");
        getContentPane().add(btnVideo, new org.netbeans.lib.awtextra.AbsoluteConstraints(359, 489, -1, -1));

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
            // Gọi DAO để cập nhật thời gian rời
            roomDao.markLeave(roomDao.getRoomIdByCode(roomCode), currentUser.getId().toString());
            // Xóa người đó khỏi danh sách hiển thị
            removeUserFromList(currentUser.getFullName());
            // Đóng phòng hoặc quay lại menu chính
            this.dispose();
            new frmMainMenu(currentUser).setVisible(true);
        }
    }//GEN-LAST:event_btnEndActionPerformed

    private void btnGuiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGuiActionPerformed
        
    }//GEN-LAST:event_btnGuiActionPerformed

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
