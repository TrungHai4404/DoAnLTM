package Client;

import java.util.concurrent.ConcurrentLinkedQueue;

public class WebcamCaptureManager {
    private static WebcamCapture webcam;
    private static final ConcurrentLinkedQueue<byte[]> sharedFrames = new ConcurrentLinkedQueue<>();
    private static volatile boolean running = false;

    public static void start() {
        if (running) return;
        webcam = new WebcamCapture();
        running = true;

        new Thread(() -> {
            while (running && webcam.isAvailable()) {
                byte[] frame = webcam.captureFrame();
                if (frame != null) {
                    sharedFrames.clear();
                    sharedFrames.add(frame);
                }
                try { Thread.sleep(33); } catch (InterruptedException ignored) {}
            }
        }, "Webcam-Shared").start();

        System.out.println("Shared webcam started");
    }

    public static byte[] getLatestFrame() {
        return sharedFrames.peek();
    }

    public static void stop() {
        running = false;
        if (webcam != null) webcam.release();
    }
}
