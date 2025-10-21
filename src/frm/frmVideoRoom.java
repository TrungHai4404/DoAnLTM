package frm;

import Client.AudioClientUDP;
import Client.ChatClientTCP;
import Client.VideoClientUDP;
import Client.WebcamCapture;
import Client.WebcamCaptureManager;
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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
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
    private String ServerIP = "192.168.1.3";
    // Networking
    private VideoClientUDP videoClient;
    private ChatClientTCP chatClient;
    private AudioClientUDP audioClient;
    // ---- Camera control flags ----
    private volatile boolean capturing = false;        // luồng gửi frame chạy/đứng
    // trạng thái cam bên phía người khác: true=ON, false=OFF
    private volatile boolean serverDisconnected = false;

    private final ConcurrentHashMap<String, Boolean> remoteCamOn = new ConcurrentHashMap<>();
    // UI
    private BufferedImage noCamImage;
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
        setLocationRelativeTo(null); 
        txtRoomID.setText(roomCode);
        txtRoomID.setEditable(false);
        loadMembers();
        list_ThanhVien.setModel(memberModel);
        initNetworking();
        
        new javax.swing.Timer(5000, e -> loadMembers()).start();
        // 🔹 Khi nhấn Enter trong ô nhập tin nhắn => tự động gửi
        txtVanBan.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent evt) {
                if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    evt.consume(); // chặn xuống dòng
                    btnGuiActionPerformed(null); // gọi sự kiện gửi
                }
            }
        });
    }
    private void initNetworking() {
        videoPanelGrid.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        noCamImage = createNoCamImage(180, 120, "Camera Off");
        //Kiểm tra webcam qua WebcamManager
        boolean webcamAvailable = true;
        try {
            WebcamCaptureManager.start(); // khởi động luồng đọc chia sẻ
            // đợi tối đa ~0.5s xem có frame chưa
            long t0 = System.currentTimeMillis();
            byte[] first = null;
            while (System.currentTimeMillis() - t0 < 500 && (first = WebcamCaptureManager.getLatestFrame()) == null) {
                Thread.sleep(50);
            }
            webcamAvailable = (first != null);
        } catch (Exception ex) {
            webcamAvailable = false;
        }

        if (!webcamAvailable) {
            videoEnabled = false;
            btnVideo.setText("Bật Video");
            // tạo khung preview local là "Camera Off"
            updateVideoPanel(localClientID, null);
        }
        //Kiểm tra mic
        boolean micAvailable = isMicAvailable();
        if (!micAvailable) {
            micEnabled = false;
            btnMic.setText("Bật Mic");
        }else{
            micEnabled = false;
            btnMic.setText("Bật Mic");
        }
        try {
            videoClient = new VideoClientUDP(ServerIP);
            audioClient = new AudioClientUDP(ServerIP,roomCode,localClientID);
            chatClient = new ChatClientTCP(ServerIP);
            //Kiểm tra kết nối đến server UDP
            audioClient.setConnectionListener(type -> { SwingUtilities.invokeLater(() -> handleServerDisconnect(type));});
            videoClient.setConnectionListener(type -> { SwingUtilities.invokeLater(() -> handleServerDisconnect(type));});
            // Gửi thông điệp JOIN
            chatClient.sendMessage("JOIN:" + localClientID + "|" + roomCode);
            chatClient.sendMessage(videoEnabled ? "CAM_ON:" + localClientID : "CAM_OFF:" + localClientID);

            audioClient.start();
            capturing = videoEnabled;
            // Gửi video
            new Thread(() -> {
                try {
                    long lastTime = System.currentTimeMillis();
                    while (true) {
                        if (!capturing || !videoEnabled) {
                            Thread.sleep(80);
                            continue;
                        }

                        byte[] frameData = WebcamCaptureManager.getLatestFrame();
                        if (frameData != null && frameData.length > 0) {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(frameData));
                            if (img == null) continue;
                            BufferedImage resized = resizeFrame(img, 180, 120);

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                            if (!writers.hasNext()) continue;
                            ImageWriter writer = writers.next();
                            ImageWriteParam param = writer.getDefaultWriteParam();
                            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            param.setCompressionQuality(0.85f);
                            writer.setOutput(ImageIO.createImageOutputStream(baos));
                            writer.write(null, new IIOImage(resized, null, null), param);
                            writer.dispose();

                            videoClient.sendFrame(baos.toByteArray(), localClientID,roomCode);
                            SwingUtilities.invokeLater(() -> updateVideoPanel(localClientID, resized));
                        }
                        // ✅ Giữ tốc độ khung hình ~30fps
                        long frameTime = 33 - (System.currentTimeMillis() - lastTime);
                        if (frameTime > 0) Thread.sleep(frameTime);
                        lastTime = System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    System.err.println("❌ Lỗi gửi video: " + e.getMessage());
                }
            }).start();

            // 🧠 Thread nhận video (UDP Receive)
            new Thread(() -> {
                try {
                    byte[] buf = new byte[65536];
                    long lastFrameTime = System.currentTimeMillis();
                    int frameCount = 0;
                    long lastFpsCheck = System.currentTimeMillis();

                    while (true) {
                        DatagramPacket pkt = videoClient.receiveFrame(buf);
                        if (pkt == null) {
                            Thread.sleep(10);
                            continue;
                        }

                        byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                        if (data.length <= 72) continue;

                        // 🧩 Tách roomCode và clientID
                        String roomCodeFrame = new String(Arrays.copyOfRange(data, 0, 36)).trim();
                        String clientID = new String(Arrays.copyOfRange(data, 36, 72)).trim();
                        //Bỏ qua frame cùng phòng
                        if (!roomCodeFrame.equals(this.roomCode)) continue;

                        // Nếu user tắt cam thì bỏ qua frame
                        if (!remoteCamOn.getOrDefault(clientID, true)) {
                            continue;
                        }

                        // 🧩 Giải mã khung hình
                        byte[] frameBytes = Arrays.copyOfRange(data, 72, data.length);
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(frameBytes)) {
                            BufferedImage img = ImageIO.read(bais);
                            if (img != null) {
                                SwingUtilities.invokeLater(() -> updateVideoPanel(clientID, img));
                            } else {
                                System.err.println("Frame lỗi hoặc không hợp lệ từ: " + clientID);
                            }
                        }

                        // ✅ FPS debug nhẹ
                        frameCount++;
                        if (System.currentTimeMillis() - lastFpsCheck > 1000) {
                            System.out.println("FPS nhận (" + clientID + "): " + frameCount);
                            frameCount = 0;
                            lastFpsCheck = System.currentTimeMillis();
                        }
                        // Điều chỉnh tốc độ nhận cho mượt hơn
                        long delta = System.currentTimeMillis() - lastFrameTime;
                        if (delta < 30) Thread.sleep(30 - delta);
                        lastFrameTime = System.currentTimeMillis();
                    }

                } catch (SocketException e) {
                    System.err.println("❌ Mất kết nối tới Video Server: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> handleServerDisconnect("VIDEO"));
                } catch (IOException e) {
                    System.err.println("⚠️ Lỗi đọc video UDP: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> handleServerDisconnect("VIDEO"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Nhận tin chat & thông điệp
            new Thread(() -> {
                try {
                    while (!serverDisconnected) {
                        String msg = chatClient.receiveMessage();
                        if (msg == null) continue;
                        if (msg.startsWith("JOINED:")) {
                            final String u = msg.substring(7).trim();
                            // tạo label cho người mới (nếu chưa có)
                            SwingUtilities.invokeLater(() -> {
                                if (!videoPanels.containsKey(u)) {
                                    updateVideoPanel(u, null);
                                }
                            });

                            // nếu mình đang bật cam thì “nhá” 1–2 frame để người mới có hình ngay
                            if (videoEnabled) {
                                try {
                                    for (int i = 0; i < 2; i++) {
                                        byte[] f = WebcamCaptureManager.getLatestFrame();
                                        if (f == null) break;
                                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(f));
                                        if (img == null) break;
                                        BufferedImage resized = resizeFrame(img, 180, 120);
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        ImageIO.write(resized, "jpg", baos);
                                        videoClient.sendFrame(baos.toByteArray(), localClientID,roomCode);
                                        Thread.sleep(40);
                                    }
                                } catch (Exception ignore) {}
                            }
                            continue;
                        }
                        if (serverDisconnected) break;
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
                } catch (IOException e) {
                    if ("CHAT_SERVER_DISCONNECTED".equals(e.getMessage())) {
                        System.err.println("⚠️ Mất kết nối TCP tới Chat Server");
                        handleServerDisconnect("CHAT");
                    } else {
                        e.printStackTrace();
                        handleServerDisconnect("CHAT");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    handleServerDisconnect("CHAT");
                }
            }).start();
        }catch (Exception e) {
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
        noCamImage = createNoCamImage(180, 120, "Camera Off");
        if (label == null) {
            // 🧩 Tạo JLabel mới cho client
            label = new JLabel("Đang kết nối...", SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(180, 120));
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
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = resizedImage.createGraphics();

        // Tùy chọn: Bật các gợi ý để có chất lượng tốt hơn
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Vẽ trực tiếp ảnh gốc vào ảnh mới với kích thước mong muốn
        g2d.drawImage(img, 0, 0, width, height, null);
        g2d.dispose();

        return resizedImage;
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        list_ThanhVien = new javax.swing.JList<>();
        jScrollPane2 = new javax.swing.JScrollPane();
        txt_KhungChat = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        txtVanBan = new javax.swing.JTextField();
        btnGui = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        videoPanelGrid = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jPanel5 = new javax.swing.JPanel();
        btnMic = new javax.swing.JButton();
        btnVideo = new javax.swing.JButton();
        btnEnd = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        txtRoomID = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(new java.awt.Color(255, 255, 255));
        setMinimumSize(new java.awt.Dimension(1200, 600));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel1.setBackground(new java.awt.Color(204, 255, 204));
        jPanel1.setBorder(new javax.swing.border.MatteBorder(null));

        jScrollPane1.setViewportView(list_ThanhVien);

        txt_KhungChat.setColumns(20);
        txt_KhungChat.setRows(5);
        jScrollPane2.setViewportView(txt_KhungChat);

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel1.setText("Danh sách TV");

        txtVanBan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtVanBanActionPerformed(evt);
            }
        });

        btnGui.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        btnGui.setText("Gửi");
        btnGui.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGuiActionPerformed(evt);
            }
        });

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel2.setText("Khung chat");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 222, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(txtVanBan, javax.swing.GroupLayout.PREFERRED_SIZE, 157, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnGui, javax.swing.GroupLayout.PREFERRED_SIZE, 57, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(75, 75, 75))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel2)
                .addGap(1, 1, 1)
                .addComponent(jScrollPane2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnGui, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtVanBan, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8))
        );

        jPanel3.setBackground(new java.awt.Color(204, 255, 255));
        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 5), "MEETING ROOM", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Segoe UI", 1, 24))); // NOI18N

        videoPanelGrid.setBackground(new java.awt.Color(204, 255, 255));
        videoPanelGrid.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        videoPanelGrid.add(jPanel2);

        jPanel5.setBackground(new java.awt.Color(0, 153, 153));

        btnMic.setText("Tắt Mic");
        btnMic.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMicActionPerformed(evt);
            }
        });

        btnVideo.setText("Tắt Video");
        btnVideo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnVideoActionPerformed(evt);
            }
        });

        btnEnd.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnEnd.setForeground(javax.swing.UIManager.getDefaults().getColor("Actions.Red"));
        btnEnd.setText("Kết thúc");
        btnEnd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEndActionPerformed(evt);
            }
        });

        jPanel4.setBackground(new java.awt.Color(0, 153, 153));

        jLabel3.setText("Mã phòng");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(35, 35, 35)
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(txtRoomID, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtRoomID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(17, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(139, 139, 139)
                .addComponent(btnMic, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(77, 77, 77)
                .addComponent(btnVideo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(72, 72, 72)
                .addComponent(btnEnd, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(254, 254, 254))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel4, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btnVideo, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(btnEnd, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(btnMic, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addComponent(videoPanelGrid, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(videoPanelGrid, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(15, 15, 15)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnEndActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEndActionPerformed
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn rời phòng không?",
                "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                serverDisconnected = true; 
                capturing = false;
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                chatClient.sendMessage("EXIT:" + localClientID + "|" + roomCode);
                if (audioClient != null) audioClient.stop();
                if (videoClient != null) videoClient.close(); // ✅ THÊM DÒNG NÀY
                if (chatClient != null) chatClient.close();
               SwingUtilities.invokeLater(() -> {
                    removeVideoPanel(localClientID);
                    removeUserFromList(localClientID);
                });
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
            serverDisconnected = true;
            capturing = false;
            try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            if (chatClient != null) {
                String exitMsg = "EXIT:" + localClientID+ "|" + roomCode;
                chatClient.sendMessage(exitMsg);
            }            
            noCamImage = createNoCamImage(180, 120, "Camera Off");
            //WebcamCaptureManager.stop();
            updateVideoPanel(localClientID, noCamImage);
            if (audioClient != null) audioClient.stop();
            if (videoClient != null) videoClient.close(); // ✅ THÊM DÒNG NÀY
            if (chatClient != null) chatClient.close();
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
            // TẮT
            videoEnabled = false;
            capturing = false;
            chatClient.sendMessage("CAM_OFF:" + localClientID);
            SwingUtilities.invokeLater(() -> updateVideoPanel(localClientID, null));
            btnVideo.setText("Bật Video");
            System.out.println("🔇 Camera OFF");
        } else {
            // BẬT (manager đã chạy sẵn từ init; nếu chưa có frame, cố đợi ngắn)
            long t0 = System.currentTimeMillis();
            while (WebcamCaptureManager.getLatestFrame() == null &&
                   System.currentTimeMillis() - t0 < 500) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            if (WebcamCaptureManager.getLatestFrame() == null) {
                JOptionPane.showMessageDialog(this, "Không thể khởi động webcam!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            videoEnabled = true;
            capturing = true;
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

    private void txtVanBanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtVanBanActionPerformed
        
    }//GEN-LAST:event_txtVanBanActionPerformed
    // Hàm kiểm tra mic
    private boolean isMicAvailable() {
        try {
            AudioFormat format = new AudioFormat(16000.0F, 16, 1, true, false);
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
    
    private synchronized void handleServerDisconnect(String reason) {
        if (serverDisconnected) return;
        serverDisconnected = true;
        System.err.println("⚠️ Server disconnected: " + reason);
        capturing = false;
        new Thread(() -> {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    JOptionPane.showMessageDialog(
                        this,
                        "🔌 Mất kết nối tới máy chủ.\nỨng dụng sẽ quay lại màn hình chính.",
                        "Mất kết nối",
                        JOptionPane.ERROR_MESSAGE
                    );
                });

                try { roomDao.markLeave(roomCode, currentUser.getId()); } catch (Exception ignored) {}
                if (audioClient != null) audioClient.stop();
                if (chatClient != null) chatClient.close();
                if (videoClient != null) videoClient.close();

                SwingUtilities.invokeLater(() -> {
                    new frmMainMenu(currentUser).setVisible(true);
                    dispose();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    public void closeRoom() {
        try {
            if (chatClient != null) chatClient.sendMessage("EXIT:" + localClientID + "|" + roomCode);
            if (audioClient != null) audioClient.stop();
            if (videoClient != null) videoClient.close();
            System.out.println("Room closed cleanly: " + roomCode);
        } catch (Exception e) {
            e.printStackTrace();
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
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<String> list_ThanhVien;
    private javax.swing.JTextField txtRoomID;
    private javax.swing.JTextField txtVanBan;
    private javax.swing.JTextArea txt_KhungChat;
    private javax.swing.JPanel videoPanelGrid;
    // End of variables declaration//GEN-END:variables
}
