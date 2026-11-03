package traffic;

import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Reads speed (km/h) from a serial RADAR module (HB100 + Arduino/ESP32, OPS243, etc.).
 * Expected format: one number per line, e.g. "42.5".
 * Change the parsing in the reader thread if your sensor prints something different.
 */
public class SerialRadar implements Radar {
    private volatile Double latest = null;
    private volatile long stamp = 0;
    private final long maxAgeMs = 1000;

    public SerialRadar(String portName, int baud) {
        SerialPort port = SerialPort.getCommPort(portName);
        port.setBaudRate(baud);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
        if (!port.openPort()) {
            throw new IllegalStateException("Could not open serial port " + portName);
        }
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(port.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        latest = Math.abs(Double.parseDouble(line.trim()));
                        stamp = System.currentTimeMillis();
                    } catch (NumberFormatException ignored) { }
                }
            } catch (Exception e) {
                System.err.println("RADAR reader stopped: " + e.getMessage());
            }
        }, "radar-reader");
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public Double read(Double cameraHint) {
        Double v = latest;
        if (v != null && System.currentTimeMillis() - stamp <= maxAgeMs) return v;
        return null;
    }
}
