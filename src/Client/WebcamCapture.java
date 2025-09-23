
package Client;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import java.io.ByteArrayOutputStream;
import org.opencv.core.MatOfByte;

public class WebcamCapture {
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    private VideoCapture camera;

    public WebcamCapture() {
        camera = new VideoCapture(0); // 0 = webcam mặc định
        if (!camera.isOpened()) {
            System.out.println("Không tìm thấy webcam!");
            System.exit(0);
        }
    }

    public byte[] captureFrame() throws Exception {
        Mat frame = new Mat();
        if (camera.read(frame)) {
            // Encode frame sang JPEG
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buffer);
            return buffer.toArray();
        }
        return null;
    }

    public void release() {
        camera.release();
    }
}
