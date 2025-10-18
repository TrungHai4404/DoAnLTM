package Client;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

public class WebcamCapture {
    private VideoCapture camera;
    private boolean available;

    public WebcamCapture() {
        try {
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
            camera = new VideoCapture(0);

            if (!camera.isOpened()) {
                System.err.println("🚫 Không tìm thấy webcam hoặc không thể mở!");
                available = false;
                camera.release();
            } else {
                available = true;
                System.out.println("✅ Webcam đã khởi tạo thành công!");
            }
        } catch (Exception e) {
            available = false;
            System.err.println("⚠️ Webcam không khả dụng: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public byte[] captureFrame() {
        if (!available) return null;
        try {
            Mat frame = new Mat();
            if (camera.read(frame)) {
                Mat frameRGB = new Mat();
                Imgproc.cvtColor(frame, frameRGB, Imgproc.COLOR_BGR2RGB);
                BufferedImage img = matToBufferedImage(frameRGB);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Lỗi khi đọc khung hình: " + e.getMessage());
        }
        return null;
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1)
            type = BufferedImage.TYPE_BYTE_GRAY;

        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] b = new byte[bufferSize];
        mat.get(0, 0, b);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), b);
        return image;
    }

    public void release() {
        if (camera != null && camera.isOpened()) {
            camera.release();
            System.out.println("🔌 Webcam released.");
        }
    }
}
