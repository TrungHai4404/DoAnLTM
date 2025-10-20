package Client;

import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;

import javax.swing.*;
import java.util.Arrays;

public class WebcamCapture {
    private VideoCapture camera;
    private boolean available = false;
    private static final int FRAME_WIDTH = 1280; //1280 - 640 - 320
    private static final int FRAME_HEIGHT = 720; //720 - 480 - 240
    private static final int MAX_RETRY = 8;
    private static final int RETRY_DELAY_MS = 60;

    public WebcamCapture() {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            System.out.println("Webcam initializing...");

            // 🧩 Thử MSMF trước (ổn định trên Windows 10+)
            camera = new VideoCapture(0, Videoio.CAP_MSMF);
            Thread.sleep(300);

            // Nếu MSMF không hoạt động, fallback sang DSHOW
            if (!camera.isOpened()) {
                System.out.println("MSMF backend failed, try DSHOW...");
                camera.release();
                camera = new VideoCapture(0, Videoio.CAP_DSHOW);
                Thread.sleep(300);
            }

            if (!camera.isOpened()) {
                showCameraError("❌ Không thể mở webcam.\nVui lòng kiểm tra xem camera có đang bị ứng dụng khác sử dụng (Zoom, OBS, Teams,...) không.");
                available = false;
                return;
            }

            camera.set(Videoio.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH);
            camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT);
            camera.set(Videoio.CAP_PROP_FPS, 15);

            // 🧩 Đọc thử khung đầu tiên
            Mat test = new Mat();
            boolean success = camera.read(test);
            if (!success || test.empty() || test.width() < 10) {
                showCameraError("⚠️ Webcam đã khởi tạo nhưng không có hình ảnh.\nHãy tắt các ứng dụng khác đang dùng camera rồi thử lại.");
                available = false;
                camera.release();
                return;
            }

            available = true;
            System.out.println("Webcam init successful (" + FRAME_WIDTH + "x" + FRAME_HEIGHT + " 15fps)");
            test.release();

        } catch (Exception e) {
            available = false;
            showCameraError("Webcam init failed: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public byte[] captureFrame() {
        if (!available || camera == null) return null;

        try {
            Mat frame = new Mat();
            int retry = 0;
            boolean success = false;

            while (retry < MAX_RETRY) {
                if (camera.read(frame) && !frame.empty()) {
                    success = true;
                    break;
                }
                retry++;
                Thread.sleep(RETRY_DELAY_MS);
            }

            if (!success || frame.empty()) {
                System.err.println("Không thể đọc khung hình sau " + MAX_RETRY + " lần thử!");
                return null;
            }

            // 🧩 Phát hiện màu tím (frame lỗi)
            Scalar meanColor = Core.mean(frame);
            if (isPurpleFrame(meanColor)) {
                System.err.println("Frame màu tím → Có thể camera đang bị ứng dụng khác chiếm dụng!");
                return null;
            }

            // 🧩 Chuyển định dạng nếu camera trả về dạng khác
            if (frame.channels() == 1) {
                Imgproc.cvtColor(frame, frame, Imgproc.COLOR_GRAY2BGR);
            } else if (frame.channels() == 2) {
                Imgproc.cvtColor(frame, frame, Imgproc.COLOR_YUV2BGR_YUY2);
            }

            Imgproc.GaussianBlur(frame, frame, new Size(3, 3), 0);
            Imgproc.resize(frame, frame, new Size(FRAME_WIDTH, FRAME_HEIGHT));

            MatOfByte buf = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buf,
                    new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 85)); // rõ hơn
            return buf.toArray();

        } catch (Exception e) {
            System.err.println("Lỗi đọc khung hình: " + e.getMessage());
            return null;
        }
    }

    private boolean isPurpleFrame(Scalar mean) {
        // Phát hiện màu tím (đỏ và xanh cao, xanh lá thấp)
        return mean.val[0] > 150 && mean.val[2] > 150 && mean.val[1] < 70;
    }

    private void showCameraError(String message) {
        System.err.println(message);
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(null, message, "Lỗi thiết bị webcam", JOptionPane.ERROR_MESSAGE)
        );
    }

    public void release() {
        if (camera != null && camera.isOpened()) {
            camera.release();
            System.out.println("📷 Webcam released.");
        }
    }
}
