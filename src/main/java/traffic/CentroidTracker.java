package traffic;

import java.util.*;

/** Simple multi-object tracker: matches detections to tracks by nearest centroid. */
public class CentroidTracker {

    public static class Track {
        public final int id;
        public int classId;
        public double cx, cy, prevCy;
        public int x1, y1, x2, y2;
        public int missed = 0;
        public boolean counted = false;
        public Double speed = null;      // latest fused speed (km/h)
        public String source = "none";   // camera / radar / fused
        public final Deque<double[]> history = new ArrayDeque<>(); // {cx, cy}

        Track(int id, Detection d) {
            this.id = id;
            this.cx = d.cx();
            this.cy = d.cy();
            this.prevCy = this.cy;
            apply(d);
        }

        void apply(Detection d) {
            classId = d.classId();
            x1 = d.x1(); y1 = d.y1(); x2 = d.x2(); y2 = d.y2();
            prevCy = cy;
            cx = d.cx();
            cy = d.cy();
            history.addLast(new double[]{cx, cy});
            while (history.size() > 15) history.removeFirst();
        }
    }

    private final Map<Integer, Track> tracks = new LinkedHashMap<>();
    private int nextId = 1;
    private final double maxDistance;
    private final int maxMissed;

    public CentroidTracker(double maxDistance, int maxMissed) {
        this.maxDistance = maxDistance;
        this.maxMissed = maxMissed;
    }

    public Collection<Track> update(List<Detection> dets) {
        record Pair(double dist, int trackId, int detIdx) {}
        List<Pair> pairs = new ArrayList<>();
        for (Track t : tracks.values()) {
            for (int i = 0; i < dets.size(); i++) {
                double dist = Math.hypot(t.cx - dets.get(i).cx(), t.cy - dets.get(i).cy());
                if (dist < maxDistance) pairs.add(new Pair(dist, t.id, i));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::dist));

        Set<Integer> usedTracks = new HashSet<>();
        Set<Integer> usedDets = new HashSet<>();
        for (Pair p : pairs) {
            if (usedTracks.contains(p.trackId()) || usedDets.contains(p.detIdx())) continue;
            tracks.get(p.trackId()).apply(dets.get(p.detIdx()));
            tracks.get(p.trackId()).missed = 0;
            usedTracks.add(p.trackId());
            usedDets.add(p.detIdx());
        }

        for (Track t : tracks.values()) {
            if (!usedTracks.contains(t.id)) t.missed++;
        }
        tracks.values().removeIf(t -> t.missed > maxMissed);

        for (int i = 0; i < dets.size(); i++) {
            if (!usedDets.contains(i)) {
                Track t = new Track(nextId++, dets.get(i));
                tracks.put(t.id, t);
            }
        }
        return tracks.values();
    }
}
