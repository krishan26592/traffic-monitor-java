package traffic;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Vehicle detection using a YOLOv8 ONNX model through OpenCV's DNN module. */
public class VehicleDetector {
    private static final int SIZE = 640;
    // COCO ids: 2 = car, 3 = motorcycle, 5 = bus, 7 = truck
    private static final Set<Integer> VEHICLES = Set.of(2, 3, 5, 7);

    private final Net net;
    private final float confThreshold;
    private final float nmsThreshold = 0.45f;

    public VehicleDetector(String onnxPath, float confThreshold) {
        this.net = Dnn.readNetFromONNX(onnxPath);
        this.confThreshold = confThreshold;
    }

    public List<Detection> detect(Mat frame) {
        Mat blob = Dnn.blobFromImage(frame, 1.0 / 255.0, new Size(SIZE, SIZE),
                new Scalar(0, 0, 0), true, false);
        net.setInput(blob);
        Mat out = net.forward();                 // shape 1 x 84 x 8400

        int dims = out.size(1);                  // 84 = 4 box values + 80 class scores
        Mat m = out.reshape(1, dims);            // 84 x 8400
        Mat t = new Mat();
        Core.transpose(m, t);                    // 8400 x 84

        int n = t.rows();
        int cols = t.cols();
        float[] d = new float[n * cols];
        t.get(0, 0, d);

        double sx = frame.cols() / (double) SIZE;
        double sy = frame.rows() / (double) SIZE;

        List<Rect2d> boxes = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        List<Integer> classes = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            int base = i * cols;
            float best = 0;
            int bestCls = -1;
            for (int c : VEHICLES) {
                float s = d[base + 4 + c];
                if (s > best) { best = s; bestCls = c; }
            }
            if (best < confThreshold) continue;

            double cx = d[base] * sx, cy = d[base + 1] * sy;
            double w = d[base + 2] * sx, h = d[base + 3] * sy;
            boxes.add(new Rect2d(cx - w / 2, cy - h / 2, w, h));
            scores.add(best);
            classes.add(bestCls);
        }

        List<Detection> result = new ArrayList<>();
        if (boxes.isEmpty()) return result;

        MatOfRect2d boxMat = new MatOfRect2d();
        boxMat.fromList(boxes);
        MatOfFloat scoreMat = new MatOfFloat();
        scoreMat.fromList(scores);
        MatOfInt keep = new MatOfInt();
        Dnn.NMSBoxes(boxMat, scoreMat, confThreshold, nmsThreshold, keep);

        for (int idx : keep.toArray()) {
            Rect2d r = boxes.get(idx);
            result.add(new Detection((int) r.x, (int) r.y,
                    (int) (r.x + r.width), (int) (r.y + r.height),
                    classes.get(idx), scores.get(idx)));
        }
        return result;
    }
}
