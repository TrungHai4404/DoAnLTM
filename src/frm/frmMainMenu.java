package frm;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
public class frmMainMenu extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(frmMainMenu.class.getName());
    public frmMainMenu() {
        initComponents();
    }
    private void openVideoRoom(String roomID, boolean isHost) {
        frmVideoRoom roomForm = new frmVideoRoom(roomID, isHost);
        roomForm.setVisible(true);
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        btnTaoCuocGoi = new javax.swing.JButton();
        btnThamGiaCuocGoi = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenu2 = new javax.swing.JMenu();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 36)); // NOI18N
        jLabel1.setText("MEETING");

        btnTaoCuocGoi.setText("Tạo cuộc gọi mới");
        btnTaoCuocGoi.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTaoCuocGoiActionPerformed(evt);
            }
        });

        btnThamGiaCuocGoi.setText("Tham gia cuộc gọi");
        btnThamGiaCuocGoi.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnThamGiaCuocGoiActionPerformed(evt);
            }
        });

        jMenu1.setText("Hệ thống");
        jMenuBar1.add(jMenu1);

        jMenu2.setText("Chức năng");
        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addComponent(btnTaoCuocGoi)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnThamGiaCuocGoi)
                .addGap(32, 32, 32))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(151, Short.MAX_VALUE)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(146, 146, 146))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnTaoCuocGoi)
                    .addComponent(btnThamGiaCuocGoi))
                .addContainerGap(41, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnThamGiaCuocGoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnThamGiaCuocGoiActionPerformed
       // Tham gia cuộc gọi
        btnThamGiaCuocGoi.addActionListener(e -> {
            String roomID = JOptionPane.showInputDialog(this, "Nhập Room ID:");
            if (roomID != null && !roomID.isEmpty()) {
                openVideoRoom(roomID, false);
            }
        });
    }//GEN-LAST:event_btnThamGiaCuocGoiActionPerformed

    private void btnTaoCuocGoiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTaoCuocGoiActionPerformed
        btnTaoCuocGoi.addActionListener(e -> {
            String roomID = "ROOM_" + System.currentTimeMillis(); // tạo ID phòng
            openVideoRoom(roomID, true);
        });
    }//GEN-LAST:event_btnTaoCuocGoiActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        SwingUtilities.invokeLater(() -> new frmMainMenu().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnTaoCuocGoi;
    private javax.swing.JButton btnThamGiaCuocGoi;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    // End of variables declaration//GEN-END:variables
}
