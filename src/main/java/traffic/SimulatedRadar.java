package traffic;

import java.util.Random;

/** Fake RADAR for demos when no hardware is connected. */
public class SimulatedRadar implements Radar {
    private final Random rnd = new Random();

    @Override
    public Double read(Double cameraHint) {
        if (cameraHint == null) return null;
        return Math.max(0, cameraHint * (1 + rnd.nextGaussian() * 0.05));
    }
}
