package frm;

import java.util.UUID;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import model.User;
import org.opencv.calib3d.UsacParams;
import dao.VideoRoomDao;
public class frmMainMenu extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(frmMainMenu.class.getName());
    private final User currentUser; // giữ thông tin người dùng đang đăng nhập
    VideoRoomDao roomDao = new VideoRoomDao();
     
    public frmMainMenu(User user) {
        initComponents();
        this.currentUser = user;
        txtUsername.setText(user.getUsername().toString());
        txtFullname.setText(user.getFullName().toString());
        txtEmail.setText(user.getEmail().toString());
        
    }
    private void openVideoRoom(String roomCode, boolean isHost) {
        try {
            frmVideoRoom videoRoom = new frmVideoRoom(roomCode, isHost, currentUser);
            videoRoom.setVisible(true);
            this.dispose(); // đóng menu chính
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi mở phòng: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnTaoCuocGoi = new javax.swing.JButton();
        btnThamGiaCuocGoi = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        txtUsername = new javax.swing.JTextField();
        txtFullname = new javax.swing.JTextField();
        txtEmail = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        btnDoiMK = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        btnTaoCuocGoi.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        btnTaoCuocGoi.setText("Tạo cuộc gọi mới");
        btnTaoCuocGoi.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTaoCuocGoiActionPerformed(evt);
            }
        });

        btnThamGiaCuocGoi.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        btnThamGiaCuocGoi.setText("Tham gia cuộc gọi");
        btnThamGiaCuocGoi.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnThamGiaCuocGoiActionPerformed(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel3.setText("Tên đăng nhập");

        jLabel4.setText("Họ tên");

        jLabel5.setText("Email");

        txtUsername.setEditable(false);

        txtFullname.setEditable(false);

        txtEmail.setEditable(false);

        jButton1.setText("Đăng xuất");

        btnDoiMK.setText("Đổi mật khẩu");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(txtUsername, javax.swing.GroupLayout.DEFAULT_SIZE, 223, Short.MAX_VALUE)
                    .addComponent(txtFullname)
                    .addComponent(txtEmail))
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnDoiMK)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(txtUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(txtFullname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnDoiMK))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(txtEmail, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton1))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel1.setText("Wellcome to VideoChatProMax");

        jMenu1.setText("Hệ thống");

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
        jMenuItem1.setText("Thoát");
        jMenu1.add(jMenuItem1);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Chức năng");
        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(btnTaoCuocGoi)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addGap(49, 49, 49)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnThamGiaCuocGoi)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 362, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(53, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnTaoCuocGoi, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnThamGiaCuocGoi, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(21, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnThamGiaCuocGoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnThamGiaCuocGoiActionPerformed
        String roomCode = JOptionPane.showInputDialog(this, "Nhập mã phòng:");
        if (roomCode != null && !roomCode.trim().isEmpty()) {
            String roomID = roomDao.getRoomIdByCode(roomCode.trim());
            if (roomID == null) {
                JOptionPane.showMessageDialog(this, "Phòng không tồn tại!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 1️⃣ Thêm người này vào bảng RoomMembers
            roomDao.addMember(roomCode, currentUser.getId());

            JOptionPane.showMessageDialog(this, "Tham gia phòng thành công!");
            openVideoRoom(roomCode.trim(), false);
        }
    }//GEN-LAST:event_btnThamGiaCuocGoiActionPerformed

    private void btnTaoCuocGoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTaoCuocGoiActionPerformed
        try {
            // 1️⃣ Tạo phòng trong DB
            String roomCode = roomDao.createRoom(currentUser.getId());
            if (roomCode == null) {
                JOptionPane.showMessageDialog(this, "Không thể tạo phòng mới.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 2️⃣ Lấy RoomID từ RoomCode
//            String roomID = roomDao.getRoomIdByCode(roomCode);

            // 3️⃣ Thêm chính người tạo vào danh sách thành viên
            roomDao.addMember(roomCode, currentUser.getId());

            JOptionPane.showMessageDialog(this, "Tạo phòng thành công!\nMã phòng: " + roomCode);
            openVideoRoom(roomCode, true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi tạo cuộc gọi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnTaoCuocGoiActionPerformed

//    public static void main(String args[]) {
//        SwingUtilities.invokeLater(() -> new frmMainMenu().setVisible(true));
//    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnDoiMK;
    private javax.swing.JButton btnTaoCuocGoi;
    private javax.swing.JButton btnThamGiaCuocGoi;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTextField txtEmail;
    private javax.swing.JTextField txtFullname;
    private javax.swing.JTextField txtUsername;
    // End of variables declaration//GEN-END:variables
}
