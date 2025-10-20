package Client;

import frm.frmVideoRoom;
import model.User;

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
            videoRoom = new frmVideoRoom(roomCode, isHost, user);
            videoRoom.setVisible(true);
            System.out.println("Room started: " + roomCode);
        } catch (Exception e) {
            System.err.println("Room error (" + roomCode + "): " + e.getMessage());
        }
    }

    public void start() {
        if (thread == null) {
            thread = new Thread(this, "Room-" + roomCode);
            thread.start();
        }
    }

    public void stop() {
        try {
            if (videoRoom != null) videoRoom.closeRoom();
            if (thread != null && thread.isAlive()) thread.interrupt();
            System.out.println("Room stopped: " + roomCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
