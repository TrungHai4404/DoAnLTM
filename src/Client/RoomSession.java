package Client;

import frm.frmVideoRoom;
import model.User;
import javax.swing.JOptionPane;

public class RoomSession implements Runnable {
    private Thread thread;
    private final String roomCode;
    private final boolean isHost;
    private final User user;
    private frmVideoRoom videoRoom;

    public RoomSession(String roomCode, boolean isHost, User user) {
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.user = user;
    }

    @Override
    public void run() {
        try {
            // ✅ Kiểm tra user có đang ở phòng này chưa
            if (!RoomSessionManager.registerUserToRoom(user.getUsername(), roomCode)) {
                JOptionPane.showMessageDialog(null,
                        "Bạn đã tham gia phòng " + roomCode + " bằng tài khoản này.\nKhông thể mở thêm phiên mới cho cùng phòng.",
                        "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 🚀 Mở form phòng họp
            videoRoom = new frmVideoRoom(roomCode, isHost, user);
            videoRoom.setVisible(true);
            System.out.println("✅ Room started: " + roomCode);

        } catch (Exception e) {
            System.err.println("❌ Room error (" + roomCode + "): " + e.getMessage());
            cleanup();
        }
    }

    public void start() {
        if (thread == null) {
            thread = new Thread(this, "Room-" + roomCode);
            thread.start();
        }
    }

    public void stop() {
        cleanup();
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        System.out.println("🛑 Room stopped: " + roomCode);
    }

    private void cleanup() {
        try {
            if (videoRoom != null) {
                videoRoom.closeRoom();
                videoRoom = null;
            }
        } catch (Exception ignored) {}

        // 🔁 Giải phóng tài nguyên
        RoomSessionManager.removeUserFromRoom(user.getUsername(), roomCode);
        RoomSessionManager.releaseWebcam("device0");
    }
}
