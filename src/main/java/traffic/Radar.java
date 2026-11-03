package traffic;

/** Any RADAR source: real hardware or simulated. */
public interface Radar {
    /**
     * @param cameraHint camera speed estimate (used only by the simulator)
     * @return speed in km/h, or null if no valid reading
     */
    Double read(Double cameraHint);
}
