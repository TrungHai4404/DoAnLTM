package server; 

public class mainServer {

    public static void main(String[] args) {
        System.out.println("🚀 Starting all servers...");

        // Chạy VideoServerUDP trên một luồng riêng
        new Thread(() -> {
            try {
                new VideoServerUDP();
            } catch (Exception e) {
                System.err.println("❌ Video Server failed to start:");
                e.printStackTrace();
            }
        }).start();

        // Chạy ChatServerTCP trên một luồng riêng
        new Thread(() -> {
            try {
                new ChatServerTCP();
            } catch (Exception e) {
                System.err.println("❌ Chat Server failed to start:");
                e.printStackTrace();
            }
        }).start();

        // Chạy AudioServerUDP trên một luồng riêng
        new Thread(() -> {
            try {
                new AudioServerUDP();
            } catch (Exception e) {
                System.err.println("❌ Audio Server failed to start:");
                e.printStackTrace();
            }
        }).start();

        System.out.println("✅ All servers have been launched in separate threads.");
    }
}