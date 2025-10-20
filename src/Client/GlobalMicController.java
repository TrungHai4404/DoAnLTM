package Client;

// Lớp Singleton để quản lý quyền sử dụng micro trên toàn ứng dụng
public class GlobalMicController {
    // 1. Singleton Pattern
    private static final GlobalMicController instance = new GlobalMicController();

    // 2. Lưu lại AudioClientUDP nào đang giữ quyền sử dụng micro
    private AudioClientUDP currentMicHolder = null;

    private GlobalMicController() {}

    public static GlobalMicController getInstance() {
        return instance;
    }

    /**
     * Một AudioClientUDP yêu cầu quyền sử dụng micro.
     * @param requester Client yêu cầu.
     * @return true nếu cấp quyền thành công, false nếu micro đã bị người khác chiếm giữ.
     */
    public synchronized boolean requestMicAccess(AudioClientUDP requester) {
        // Nếu không có ai giữ mic, hoặc người yêu cầu chính là người đang giữ mic
        if (currentMicHolder == null || currentMicHolder == requester) {
            currentMicHolder = requester;
            System.out.println("🎤 Mic access GRANTED to: " + requester.getClientID());
            return true;
        }
        
        // Nếu micro đã bị người khác chiếm giữ
        System.out.println("🎤 Mic access DENIED for: " + requester.getClientID() + ". Held by: " + currentMicHolder.getClientID());
        return false;
    }

    /**
     * Một AudioClientUDP giải phóng micro.
     * @param releaser Client giải phóng.
     */
    public synchronized void releaseMicAccess(AudioClientUDP releaser) {
        // Chỉ người đang giữ mic mới có quyền giải phóng nó
        if (currentMicHolder == releaser) {
            currentMicHolder = null;
            System.out.println("🎤 Mic access RELEASED by: " + releaser.getClientID());
        }
    }

    /**
     * Kiểm tra xem một client cụ thể có đang giữ mic không.
     */
    public synchronized boolean isHoldingMic(AudioClientUDP client) {
        return currentMicHolder == client;
    }
}