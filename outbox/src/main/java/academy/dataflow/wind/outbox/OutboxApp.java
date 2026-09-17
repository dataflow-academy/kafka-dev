package academy.dataflow.wind.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The maintenance planning service, outbox edition: every new order is written
 * to the table maintenance_order, and the event for it to the outbox table.
 * Debezium takes it from there. This service does not know Kafka.
 *
 * <p>Two things are yours: the INSERT into your outbox table (TODO 2) and,
 * later in the lab, the transaction around both writes (TODO 3). TODO 1 is
 * the outbox table itself, in outbox.sql.
 */
public final class OutboxApp {

    private static final Logger log = LoggerFactory.getLogger(OutboxApp.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/user";
    private static final String DB_USER = "planning";
    private static final String DB_PASSWORD = "planning";

    /** Decides the target topic: the Event Router routes by this column. */
    private static final String AGGREGATE_TYPE = "maintenance-order";
    private static final String EVENT_TYPE = "MaintenanceOrderScheduled";

    /** Time between the two writes, as in the dual write lab. */
    private static final long WORK_BETWEEN_WRITES_MS = 800;
    private static final long PAUSE_BETWEEN_ORDERS_MS = 200;

    private static volatile boolean running = true;
    private static final CountDownLatch stopped = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        MaintenancePlanner planner = new MaintenancePlanner();

        try (Connection db = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            // TODO 3 (later in the lab): what has to change so that an order
            // and its event are committed together - or not at all?

            if (!outboxInsertWorks(db, planner.next())) {
                System.exit(1);
            }
            installShutdownHook();
            log.info("Writing orders to maintenance_order and their events to outbox (autocommit: {})",
                    db.getAutoCommit());

            long orders = 0;
            while (running) {
                MaintenanceOrder order = planner.next();
                String name = "order %s (%s)".formatted(order.orderId().substring(0, 8), order.windTurbineId());

                insertOrder(db, order);
                log.info("{}: order written", name);
                Thread.sleep(WORK_BETWEEN_WRITES_MS);
                insertEvent(db, order);
                log.info("{}: event written", name);
                // TODO 3, continued

                orders++;
                Thread.sleep(PAUSE_BETWEEN_ORDERS_MS);
            }
            log.info("Stopped cleanly after {} orders", orders);
        } finally {
            stopped.countDown();
        }
    }

    private static void insertOrder(Connection db, MaintenanceOrder order) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(
                "INSERT INTO maintenance_order (order_id, wind_turbine_id, task, planned_for) VALUES (?, ?, ?, ?)")) {
            stmt.setObject(1, UUID.fromString(order.orderId()));
            stmt.setString(2, order.windTurbineId());
            stmt.setString(3, order.task());
            stmt.setDate(4, Date.valueOf(order.plannedFor()));
            stmt.executeUpdate();
        }
    }

    /**
     * Writes the event for one order into the outbox table.
     *
     * <p>TODO 2: insert one row into the outbox table you created. The
     * Event Router needs to know which topic the event goes to
     * (AGGREGATE_TYPE), which key it gets, what kind of event it is
     * (EVENT_TYPE) and the payload. Which value belongs in the key?
     */
    private static void insertEvent(Connection db, MaintenanceOrder order)
            throws SQLException, JsonProcessingException {
        String payload = JSON.writeValueAsString(order);

        // try (PreparedStatement stmt = db.prepareStatement(
        //         "INSERT INTO outbox (...) VALUES (...)")) {
        //     ...
        //     stmt.executeUpdate();
        // }

        throw new IllegalStateException("TODO 2 is still open - see the lab text.");
    }

    /**
     * Tries TODO 2 once and rolls it back, so a missing or broken outbox
     * insert stops the app before it writes a single order.
     */
    private static boolean outboxInsertWorks(Connection db, MaintenanceOrder probe) throws SQLException {
        boolean autoCommit = db.getAutoCommit();
        db.setAutoCommit(false);
        try {
            insertEvent(db, probe);
            return true;
        } catch (IllegalStateException | SQLException | JsonProcessingException e) {
            log.error("Cannot write to the outbox: {}", e.getMessage());
            return false;
        } finally {
            db.rollback();
            db.setAutoCommit(autoCommit);
        }
    }

    /** SIGTERM and Ctrl+C finish the current order before the app stops. */
    private static void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped.getCount() == 0) {
                return;
            }
            log.info("Shutdown signal received, finishing the current order ...");
            running = false;
            try {
                stopped.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));
    }

    private OutboxApp() {
    }
}
