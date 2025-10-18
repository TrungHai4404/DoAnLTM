package Client;

import org.opencv.core.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.opencv.videoio.Videoio;

public class WebcamCapture {
    private VideoCapture camera;
    private boolean available = false;

    public WebcamCapture() {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            camera = new VideoCapture(0);

            if (!camera.isOpened()) {
                System.err.println("Webcame not found!");
                available = false;
                return;
            }

            // Cố gắng thiết lập độ phân giải cao hơn (mà vẫn nhẹ)
            camera.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
            camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
            camera.set(Videoio.CAP_PROP_FPS, 30);

            available = true;
            System.out.println("Webcam init succesful (640x480 @30fps)");
        } catch (Exception e) {
            System.err.println("Webcam error: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public byte[] captureFrame() {
        if (!available) return null;

        try {
            Mat frame = new Mat();
            if (!camera.read(frame) || frame.empty()) {
                return null;
            }

            // Làm mượt & resize để ổn định kích thước
            Imgproc.GaussianBlur(frame, frame, new Size(3, 3), 0);
            Imgproc.resize(frame, frame, new Size(640, 480));

            // Nén JPEG với chất lượng cao (80%)
            MatOfByte buf = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buf, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80));
            return buf.toArray();

        } catch (Exception e) {
            System.err.println("Lỗi đọc khung hình: " + e.getMessage());
        }
        return null;
    }

    public void release() {
        if (camera != null && camera.isOpened()) {
            camera.release();
            System.out.println("Webcam released.");
        }
    }
}
