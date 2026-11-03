package traffic;

/** One detected vehicle in a single frame. */
public record Detection(int x1, int y1, int x2, int y2, int classId, float conf) {
    public double cx() { return (x1 + x2) / 2.0; }
    public double cy() { return (y1 + y2) / 2.0; }
}
