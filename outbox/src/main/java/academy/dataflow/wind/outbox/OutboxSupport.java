package academy.dataflow.wind.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab scaffolding — not part of the exercise. It keeps the lab observable and
 * safe to break; you would not write this in a production client.
 */
final class OutboxSupport {

    private static final Logger log = LoggerFactory.getLogger(OutboxSupport.class);

    private static final List<String> PARKS = List.of(
            "alpha-ventus:12", "nordsee-ost:15", "borkum-riffgrund:10", "arkona:8", "baltic-eagle:5");
    private static final List<String> TASKS = List.of(
            "gearbox-inspection", "blade-inspection", "oil-change", "yaw-brake-service", "rotor-bolt-check");
    private static final Random random = new Random();

    private static volatile boolean running = true;

    /** The outbox insert under test, as {@code OutboxApp::insertEvent}. */
    interface EventWriter {
        void write(Connection db, MaintenanceOrder order) throws SQLException, JsonProcessingException;
    }

    /** Makes up a maintenance order for one of the 50 turbines of the fleet. */
    static MaintenanceOrder nextOrder() {
        String[] park = PARKS.get(random.nextInt(PARKS.size())).split(":");
        int turbine = 1 + random.nextInt(Integer.parseInt(park[1]));
        return new MaintenanceOrder(
                UUID.randomUUID().toString(),
                "%s-%02d".formatted(park[0], turbine),
                TASKS.get(random.nextInt(TASKS.size())),
                LocalDate.now().plusDays(1 + random.nextInt(30)).toString());
    }

    /** A short name for an order in the log, e.g. {@code order 1a2b3c4d (arkona-03)}. */
    static String logName(MaintenanceOrder order) {
        return "order %s (%s)".formatted(order.orderId().substring(0, 8), order.windTurbineId());
    }

    /**
     * Tries the outbox insert once with a made-up order and rolls it back, so a
     * missing or broken TODO 1 stops the app before it writes a single order.
     */
    static void requireOutboxInsert(Connection db, EventWriter insertEvent) throws SQLException {
        boolean autoCommit = db.getAutoCommit();
        db.setAutoCommit(false);
        boolean works = false;
        try {
            long before = countOutbox(db);
            insertEvent.write(db, nextOrder());
            works = countOutbox(db) != before;
            if (!works) {
                log.error("TODO 1 is still open - see the lab text.");
            }
        } catch (SQLException | JsonProcessingException e) {
            log.error("Cannot write to the outbox: {}", e.getMessage());
        } finally {
            db.rollback();
            db.setAutoCommit(autoCommit);
        }
        if (!works) {
            System.exit(1);
        }
    }

    /**
     * Lets SIGTERM and Ctrl+C finish the current order before the app stops;
     * {@code kill -9} still stops it on the spot.
     */
    static void finishCurrentOrderOnShutdown() {
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!main.isAlive()) {
                return;
            }
            log.info("Shutdown signal received, finishing the current order ...");
            running = false;
            try {
                main.join(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));
    }

    /** False once a shutdown signal has arrived. */
    static boolean keepRunning() {
        return running;
    }

    /** Counts the rows the current transaction sees in the outbox. */
    private static long countOutbox(Connection db) throws SQLException {
        try (Statement stmt = db.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM outbox")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private OutboxSupport() {
    }
}
