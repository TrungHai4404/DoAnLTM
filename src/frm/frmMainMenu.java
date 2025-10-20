package frm;

import java.util.UUID;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import model.User;
import org.opencv.calib3d.UsacParams;
import Client.ChatClientTCP;
import dao.UserDao;
import dao.VideoRoomDao;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.swing.JPasswordField;
import Utils.NetworkUtils;
public class frmMainMenu extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(frmMainMenu.class.getName());
    private final User currentUser; // giữ thông tin người dùng đang đăng nhập
    NetworkUtils checkServer = new NetworkUtils();
    VideoRoomDao roomDao = new VideoRoomDao();
    String ServerIP = "192.168.57.172";
    public frmMainMenu(User user) {
        initComponents();
        setLocationRelativeTo(null); 
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
        btnDangXuat = new javax.swing.JButton();
        btnDoiMK = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        btnItemThoat = new javax.swing.JMenu();
        mItemThoat = new javax.swing.JMenuItem();

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
        txtUsername.setEnabled(false);

        txtFullname.setEditable(false);
        txtFullname.setEnabled(false);

        txtEmail.setEditable(false);
        txtEmail.setEnabled(false);

        btnDangXuat.setText("Đăng xuất");
        btnDangXuat.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDangXuatActionPerformed(evt);
            }
        });

        btnDoiMK.setText("Đổi mật khẩu");
        btnDoiMK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDoiMKActionPerformed(evt);
            }
        });

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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnDoiMK, javax.swing.GroupLayout.DEFAULT_SIZE, 114, Short.MAX_VALUE)
                    .addComponent(btnDangXuat, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(27, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel3)
                            .addComponent(txtUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(6, 6, 6)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(txtFullname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(btnDoiMK, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(txtEmail, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(btnDangXuat))
                    .addComponent(jLabel5))
                .addContainerGap(19, Short.MAX_VALUE))
        );

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel1.setText("Wellcome to MeetRoomProMax");

        btnItemThoat.setText("Hệ thống");

        mItemThoat.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
        mItemThoat.setText("Thoát");
        mItemThoat.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mItemThoatActionPerformed(evt);
            }
        });
        btnItemThoat.add(mItemThoat);

        jMenuBar1.add(btnItemThoat);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnTaoCuocGoi)
                        .addGap(48, 48, 48)
                        .addComponent(btnThamGiaCuocGoi)))
                .addGap(53, 53, 53))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnThamGiaCuocGoi, javax.swing.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                    .addComponent(btnTaoCuocGoi, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(24, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnThamGiaCuocGoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnThamGiaCuocGoiActionPerformed
        String roomCode = JOptionPane.showInputDialog(this, "Nhập mã phòng:");
        if (roomCode == null || roomCode.trim().isEmpty()) return;

        if (!checkServer.checkAllServers(ServerIP)) {
            JOptionPane.showMessageDialog(this,
                "Không thể kết nối đến một hoặc nhiều máy chủ.\n" +
                "Vui lòng kiểm tra lại kết nối mạng hoặc khởi động lại server.",
                "Lỗi kết nối",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        String roomID = roomDao.getRoomIdByCode(roomCode.trim());
        if (roomID == null) {
            JOptionPane.showMessageDialog(this, "Phòng không tồn tại!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        openVideoRoom(roomCode.trim(), false);
        
    }//GEN-LAST:event_btnThamGiaCuocGoiActionPerformed

    private void btnTaoCuocGoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTaoCuocGoiActionPerformed
        if (!checkServer.checkAllServers(ServerIP)) {
            JOptionPane.showMessageDialog(this,
                "Không thể kết nối đến một hoặc nhiều máy chủ.\n" +
                "Vui lòng kiểm tra lại kết nối mạng hoặc khởi động lại server.",
                "Lỗi kết nối",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        //Tạo phong
        try {
            // 1️⃣ Tạo phòng trong DB
            String roomCode = roomDao.createRoom(currentUser.getId());  
            if (roomCode == null) {
                JOptionPane.showMessageDialog(this, "Không thể tạo phòng mới.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(this, "Tạo phòng thành công!\nMã phòng: " + roomCode);
            openVideoRoom(roomCode, true);
        } catch (Exception ex) {    
            JOptionPane.showMessageDialog(this, "Lỗi khi tạo cuộc gọi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
     
    }//GEN-LAST:event_btnTaoCuocGoiActionPerformed

    private void mItemThoatActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mItemThoatActionPerformed
        System.exit(0);
    }//GEN-LAST:event_mItemThoatActionPerformed

    private void btnDangXuatActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDangXuatActionPerformed
         int confirm = JOptionPane.showConfirmDialog(this, 
            "Bạn có chắc chắn muốn đăng xuất không?", 
            "Đăng xuất", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                new frmLogin().setVisible(true);
                this.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }//GEN-LAST:event_btnDangXuatActionPerformed

    private void btnDoiMKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDoiMKActionPerformed
        JPasswordField oldPassField = new JPasswordField();
        JPasswordField newPassField = new JPasswordField();
        JPasswordField confirmPassField = new JPasswordField();

        Object[] message = {
            "Mật khẩu hiện tại:", oldPassField,
            "Mật khẩu mới:", newPassField,
            "Xác nhận mật khẩu mới:", confirmPassField
        };

        int option = JOptionPane.showConfirmDialog(
            this, message, "Đổi mật khẩu", JOptionPane.OK_CANCEL_OPTION
        );

        if (option == JOptionPane.OK_OPTION) {
            String oldPass = new String(oldPassField.getPassword());
            String newPass = new String(newPassField.getPassword());
            String confirmPass = new String(confirmPassField.getPassword());

            if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!newPass.equals(confirmPass)) {
                JOptionPane.showMessageDialog(this, "Mật khẩu xác nhận không khớp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                UserDao userDao = new UserDao();
                boolean success = userDao.changePassword(currentUser.getUsername(), oldPass, newPass);

                if (success) {
                    JOptionPane.showMessageDialog(this, "✅ Đổi mật khẩu thành công!");
                } else {
                    JOptionPane.showMessageDialog(this, "❌ Mật khẩu cũ không chính xác!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Đã xảy ra lỗi khi đổi mật khẩu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnDoiMKActionPerformed

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnDangXuat;
    private javax.swing.JButton btnDoiMK;
    private javax.swing.JMenu btnItemThoat;
    private javax.swing.JButton btnTaoCuocGoi;
    private javax.swing.JButton btnThamGiaCuocGoi;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JMenuItem mItemThoat;
    private javax.swing.JTextField txtEmail;
    private javax.swing.JTextField txtFullname;
    private javax.swing.JTextField txtUsername;
    // End of variables declaration//GEN-END:variables
}
