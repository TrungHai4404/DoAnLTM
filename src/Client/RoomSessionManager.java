package Client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoomSessionManager {
    // Danh sách user đang tham gia phòng (user|roomID)
    private static final Set<String> activeUserRoomPairs = ConcurrentHashMap.newKeySet();

    // Quản lý tình trạng sử dụng camera
    private static final ConcurrentHashMap<String, Boolean> webcamInUse = new ConcurrentHashMap<>();

    private static String key(String userId, String roomCode) {
        return userId + "|" + roomCode;
    }

    /** 
     * Kiểm tra xem user có đang ở cùng phòng này không 
     */
    public static boolean isUserAlreadyInRoom(String userId, String roomCode) {
        return activeUserRoomPairs.contains(key(userId, roomCode));
    }

    /**
     * Đăng ký user tham gia phòng
     * @return true nếu thành công, false nếu đã ở phòng này rồi
     */
    public static boolean registerUserToRoom(String userId, String roomCode) {
        return activeUserRoomPairs.add(key(userId, roomCode));
    }

    /**
     * Gỡ user khỏi phòng (khi rời phòng hoặc tắt phòng)
     */
    public static void removeUserFromRoom(String userId, String roomCode) {
        activeUserRoomPairs.remove(key(userId, roomCode));
    }

    // --- Quản lý Webcam ---
    public static synchronized boolean lockWebcam(String deviceName) {
        boolean inUse = webcamInUse.getOrDefault(deviceName, false);
        System.out.println("🔒 Try lock " + deviceName + " -> inUse=" + inUse);
        if (inUse) return false;
        webcamInUse.put(deviceName, true);
        return true;
    }

    public static synchronized void releaseWebcam(String deviceName) {
        System.out.println("🧹 Releasing webcam lock for " + deviceName);
        webcamInUse.put(deviceName, false);
    }
    public static synchronized void resetWebcamState() {
        webcamInUse.put("device0", false);
    }
    public static synchronized boolean isWebcamLocked(String deviceName) {
        return webcamInUse.getOrDefault(deviceName, false);
    }

}
