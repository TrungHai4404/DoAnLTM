package Client;

import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import javax.swing.*;

public class WebcamCapture {
    private VideoCapture camera;
    private boolean available = false;
    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 480;

    public WebcamCapture() {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            camera = new VideoCapture(0, Videoio.CAP_MSMF);
            Thread.sleep(200);

            if (!camera.isOpened()) {
                camera = new VideoCapture(0, Videoio.CAP_DSHOW);
                Thread.sleep(200);
            }

            if (!camera.isOpened()) {
                showError("Không thể mở webcam.\nThiết bị có thể đang được sử dụng ở phòng khác hoặc bởi ứng dụng khác.");
                available = false;
                return;
            }

            camera.set(Videoio.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH);
            camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT);
            camera.set(Videoio.CAP_PROP_FPS, 15);

            Mat test = new Mat();
            if (!camera.read(test) || test.empty()) {
                showError("Webcam không có hình ảnh.\nHãy tắt các ứng dụng khác đang dùng camera rồi thử lại.");
                camera.release();
                available = false;
                return;
            }

            available = true;
            System.out.println("📷 Webcam initialized " + FRAME_WIDTH + "x" + FRAME_HEIGHT);
            test.release();

        } catch (Exception e) {
            available = false;
            showError("Lỗi khởi tạo webcam: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public byte[] captureFrame() {
        if (!available || camera == null) return null;
        try {
            Mat frame = new Mat();
            if (!camera.read(frame) || frame.empty()) return null;

            Imgproc.resize(frame, frame, new Size(FRAME_WIDTH, FRAME_HEIGHT));
            MatOfByte buf = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buf);
            return buf.toArray();
        } catch (Exception e) {
            System.err.println("Lỗi đọc khung hình: " + e.getMessage());
            return null;
        }
    }

    private void showError(String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, msg, "Lỗi Webcam", JOptionPane.ERROR_MESSAGE)
        );
    }

    public void release() {
        if (camera != null && camera.isOpened()) {
            camera.release();
            System.out.println("📷 Webcam released.");
        }
    }
}
