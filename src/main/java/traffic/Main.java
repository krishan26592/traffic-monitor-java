package traffic;

import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.util.*;

/**
 * Smart Traffic Monitoring System Using Camera, RADAR and AI.
 *
 * Program arguments (all optional), e.g.:
 *   --source=traffic.mp4 --model=yolov8n.onnx --ppm=20 --limit=60 --radar=sim
 *   --radar=serial --port=COM3 --baud=9600 --line=0.6 --conf=0.35 --csv=traffic_log.csv
 *   --save=out.mp4   (save annotated video)
 *
 * Press 'q' in the video window to quit.
 */
public class Main {

    public static final Map<Integer, String> NAMES =
            Map.of(2, "car", 3, "motorcycle", 5, "bus", 7, "truck");

    public static void main(String[] args) throws Exception {
        Map<String, String> o = parseArgs(args);
        String source = o.getOrDefault("source", "0");
        String modelPath = o.getOrDefault("model", "yolov8n.onnx");
        double ppm = Double.parseDouble(o.getOrDefault("ppm", "20"));
        double limit = Double.parseDouble(o.getOrDefault("limit", "60"));
        double lineFrac = Double.parseDouble(o.getOrDefault("line", "0.6"));
        float conf = Float.parseFloat(o.getOrDefault("conf", "0.35"));
        String radarMode = o.getOrDefault("radar", "sim");
        String csvPath = o.getOrDefault("csv", "traffic_log.csv");
        String savePath = o.get("save");

        nu.pattern.OpenCV.loadLocally();   // loads native OpenCV bundled in the jar

        VideoCapture cap = source.matches("\\d+")
                ? new VideoCapture(Integer.parseInt(source))
                : new VideoCapture(source);
        if (!cap.isOpened()) {
            System.err.println("Cannot open source: " + source);
            return;
        }

        double fps = cap.get(Videoio.CAP_PROP_FPS);
        if (fps <= 0 || Double.isNaN(fps)) fps = 30;
        int w = (int) cap.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int h = (int) cap.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        int lineY = (int) (h * lineFrac);
        int zoneTop = (int) (h * 0.4), zoneBot = (int) (h * 0.9);   // RADAR zone

        VehicleDetector detector = new VehicleDetector(modelPath, conf);
        CentroidTracker tracker = new CentroidTracker(Math.max(w, h) * 0.08, 8);
        SpeedEstimator speedEst = new SpeedEstimator(ppm, fps);

        Radar radar = null;
        if (radarMode.equals("sim")) {
            radar = new SimulatedRadar();
        } else if (radarMode.equals("serial")) {
            radar = new SerialRadar(o.getOrDefault("port", "COM3"),
                    Integer.parseInt(o.getOrDefault("baud", "9600")));
        }

        VideoWriter writer = null;
        if (savePath != null) {
            writer = new VideoWriter(savePath, VideoWriter.fourcc('m', 'p', '4', 'v'),
                    fps, new Size(w, h));
        }

        int down = 0, up = 0;
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        Set<Integer> overspeed = new HashSet<>();

        try (CsvLogger logger = new CsvLogger(csvPath)) {
            Mat frame = new Mat();
            System.out.println("Running... press 'q' in the window to quit.");

            while (cap.read(frame) && !frame.empty()) {
                List<Detection> dets = detector.detect(frame);
                Collection<CentroidTracker.Track> tracks = tracker.update(dets);

                // 1) camera speed for every visible track
                Map<Integer, Double> camSpeed = new HashMap<>();
                for (CentroidTracker.Track t : tracks) {
                    if (t.missed == 0) camSpeed.put(t.id, speedEst.estimate(t));
                }

                // 2) RADAR measures one target: the vehicle closest to the sensor in the zone
                CentroidTracker.Track radarTarget = null;
                for (CentroidTracker.Track t : tracks) {
                    if (t.missed == 0 && t.cy >= zoneTop && t.cy <= zoneBot
                            && (radarTarget == null || t.cy > radarTarget.cy)) {
                        radarTarget = t;
                    }
                }
                Double radarVal = null;
                if (radar != null && radarTarget != null) {
                    radarVal = radar.read(camSpeed.get(radarTarget.id));
                }

                // 3) fusion, counting, drawing
                for (CentroidTracker.Track t : tracks) {
                    if (t.missed > 0) continue;

                    Double cam = camSpeed.get(t.id);
                    Double rad = (t == radarTarget) ? radarVal : null;
                    if (cam != null && rad != null) {
                        t.speed = 0.7 * rad + 0.3 * cam; t.source = "fused";
                    } else if (rad != null) {
                        t.speed = rad; t.source = "radar";
                    } else if (cam != null) {
                        t.speed = cam; t.source = "camera";
                    }

                    String direction = null;
                    if (!t.counted) {
                        if (t.prevCy < lineY && t.cy >= lineY) direction = "down";
                        else if (t.prevCy > lineY && t.cy <= lineY) direction = "up";
                    }
                    boolean over = t.speed != null && t.speed > limit;
                    if (direction != null) {
                        t.counted = true;
                        if (direction.equals("down")) down++; else up++;
                        String type = NAMES.getOrDefault(t.classId, "vehicle");
                        typeCounts.merge(type, 1, Integer::sum);
                        if (over) overspeed.add(t.id);
                        logger.log(t.id, type, direction, t.speed, t.source, over);
                    }

                    Scalar color = over ? new Scalar(0, 0, 255) : new Scalar(0, 200, 0);
                    Imgproc.rectangle(frame, new Point(t.x1, t.y1), new Point(t.x2, t.y2), color, 2);
                    String label = "#" + t.id + " " + NAMES.getOrDefault(t.classId, "vehicle");
                    if (t.speed != null) {
                        label += String.format(" %.0fkm/h (%s)", t.speed,
                                t.source.substring(0, 1).toUpperCase());
                    }
                    Imgproc.putText(frame, label, new Point(t.x1, Math.max(t.y1 - 6, 12)),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 2);
                }

                // overlay
                Imgproc.line(frame, new Point(0, lineY), new Point(w, lineY), new Scalar(255, 255, 0), 2);
                Imgproc.rectangle(frame, new Point(0, zoneTop), new Point(w, zoneBot),
                        new Scalar(255, 0, 255), 1);
                String[] info = {
                        "Total: " + (down + up) + "  Down: " + down + "  Up: " + up,
                        "Overspeed: " + overspeed.size() + "  (limit " + (int) limit + " km/h)",
                        typeCounts.toString()
                };
                for (int i = 0; i < info.length; i++) {
                    Imgproc.putText(frame, info[i], new Point(10, 25 + 24 * i),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.65, new Scalar(255, 255, 255), 2);
                }

                if (writer != null) writer.write(frame);
                HighGui.imshow("Smart Traffic Monitoring", frame);
                if (HighGui.waitKey(1) == 'q') break;
            }
        }

        cap.release();
        if (writer != null) writer.release();
        HighGui.destroyAllWindows();

        System.out.println("\n=== Summary ===");
        System.out.println("Total vehicles: " + (down + up) + " (down=" + down + ", up=" + up + ")");
        System.out.println("By type: " + typeCounts);
        System.out.println("Overspeed vehicles: " + overspeed.size());
        System.out.println("Log saved to: " + csvPath);
        System.exit(0);   // HighGui keeps non-daemon threads alive
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String a : args) {
            if (a.startsWith("--") && a.contains("=")) {
                int i = a.indexOf('=');
                map.put(a.substring(2, i), a.substring(i + 1));
            }
        }
        return map;
    }
}
