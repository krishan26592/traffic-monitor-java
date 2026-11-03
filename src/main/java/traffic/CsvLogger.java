package traffic;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Writes one row per counted vehicle. */
public class CsvLogger implements AutoCloseable {
    private final PrintWriter out;

    public CsvLogger(String path) throws IOException {
        out = new PrintWriter(new FileWriter(path));
        out.println("timestamp,track_id,vehicle_type,direction,speed_kmh,speed_source,overspeed");
        out.flush();
    }

    public void log(int id, String type, String direction, Double speed, String source, boolean over) {
        out.printf("%s,%d,%s,%s,%s,%s,%b%n",
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), id, type, direction,
                speed == null ? "" : String.format("%.1f", speed), source, over);
        out.flush();
    }

    @Override
    public void close() { out.close(); }
}
