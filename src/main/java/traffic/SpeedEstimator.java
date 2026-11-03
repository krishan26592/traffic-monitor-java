package traffic;

import java.util.ArrayList;
import java.util.List;

/** Camera-based speed estimation from pixel displacement. */
public class SpeedEstimator {
    private final double pixelsPerMeter;
    private final double fps;

    public SpeedEstimator(double pixelsPerMeter, double fps) {
        this.pixelsPerMeter = pixelsPerMeter;
        this.fps = fps;
    }

    /** Returns speed in km/h, or null if there is not enough history yet. */
    public Double estimate(CentroidTracker.Track t) {
        if (t.history.size() < 5) return null;
        List<double[]> h = new ArrayList<>(t.history);
        double[] a = h.get(0);
        double[] b = h.get(h.size() - 1);
        double distPx = Math.hypot(b[0] - a[0], b[1] - a[1]);
        double seconds = (h.size() - 1) / fps;
        return (distPx / pixelsPerMeter) / seconds * 3.6;
    }
}
