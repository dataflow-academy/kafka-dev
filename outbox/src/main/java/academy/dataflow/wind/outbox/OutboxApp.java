package academy.dataflow.wind.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The maintenance planning service, outbox edition: every new order is written
 * to the table maintenance_order, and the event for it to the outbox table.
 * Debezium takes it from there. This service does not know Kafka.
 *
 * <p>Two things are yours: the INSERT into the outbox table (TODO 1) and,
 * later in the lab, the transaction around both writes (TODO 2). The tables
 * themselves come from outbox.sql.
 */
public final class OutboxApp {

    private static final Logger log = LoggerFactory.getLogger(OutboxApp.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/user";
    private static final String DB_USER = "planning";
    private static final String DB_PASSWORD = "planning";

    /** Decides the target topic: the Event Router routes by this column. */
    private static final String AGGREGATE_TYPE = "maintenance-order";

    /** The kind of event. One of the optional tasks puts it into a header. */
    private static final String EVENT_TYPE = "MaintenanceOrderScheduled";

    /** Time between the two writes, as in the dual write lab. */
    private static final long WORK_BETWEEN_WRITES_MS = 800;
    private static final long PAUSE_BETWEEN_ORDERS_MS = 200;

    public static void main(String[] args) throws Exception {
        try (Connection db = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            // TODO 2 (later in the lab): what has to change so that an order
            // and its event are committed together - or not at all?

            // Lab helper: tries TODO 1 once, rolls it back, stops if it fails.
            OutboxSupport.requireOutboxInsert(db, OutboxApp::insertEvent);
            // Lab helper: Ctrl+C lets the current order finish first.
            OutboxSupport.finishCurrentOrderOnShutdown();
            log.info("Writing orders to maintenance_order and their events to outbox (autocommit: {})",
                    db.getAutoCommit());

            long orders = 0;
            while (OutboxSupport.keepRunning()) {
                // Lab helper: a made-up order and its name for the log.
                MaintenanceOrder order = OutboxSupport.nextOrder();
                String name = OutboxSupport.logName(order);

                insertOrder(db, order);
                log.info("{}: order written", name);
                Thread.sleep(WORK_BETWEEN_WRITES_MS);
                insertEvent(db, order);
                log.info("{}: event written", name);
                // TODO 2, continued

                orders++;
                Thread.sleep(PAUSE_BETWEEN_ORDERS_MS);
            }
            log.info("Stopped cleanly after {} orders", orders);
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
     * <p>TODO 1: insert one row into the outbox table. The Event Router
     * needs to know which topic the event goes to (AGGREGATE_TYPE), which
     * key it gets and the payload. Which value belongs in the key?
     */
    private static void insertEvent(Connection db, MaintenanceOrder order)
            throws SQLException, JsonProcessingException {
        String payload = JSON.writeValueAsString(order);

        // try (PreparedStatement stmt = db.prepareStatement(
        //         "INSERT INTO outbox (...) VALUES (...)")) {
        //     ...
        //     stmt.executeUpdate();
        // }
    }

    private OutboxApp() {
    }
}
