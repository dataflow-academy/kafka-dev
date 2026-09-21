package academy.dataflow.wind.dualwrite;

import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The maintenance planning service, naive edition: every new order is written
 * to the database AND sent to Kafka - two writes, no transaction around both.
 *
 * <p>Nothing to fill in. Run it, then kill it.
 */
public final class DualWriteApp {

    private static final Logger log = LoggerFactory.getLogger(DualWriteApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "nordwind.maintenance.private.maintenance-order.event";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/user";
    private static final String DB_USER = "planning";
    private static final String DB_PASSWORD = "planning";

    /**
     * Time between the two writes - in a real service this is where the
     * mapping, a call to another system or just a GC pause happens.
     */
    private static final long WORK_BETWEEN_WRITES_MS = 800;
    private static final long PAUSE_BETWEEN_ORDERS_MS = 200;

    public static void main(String[] args) throws Exception {
        // Lab helper: Ctrl+C lets the current order finish first.
        DualWriteSupport.finishCurrentOrderOnShutdown();

        try (Connection db = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
             Producer<String, MaintenanceOrder> producer = new KafkaProducer<>(producerConfig())) {
            log.info("Writing orders to table maintenance_order and topic '{}'", TOPIC);

            long orders = 0;
            while (DualWriteSupport.keepRunning()) {
                // Lab helper: a made-up order and its name for the log.
                MaintenanceOrder order = DualWriteSupport.nextOrder();
                String name = DualWriteSupport.logName(order);

                insert(db, order);
                log.info("{}: database ✓", name);

                Thread.sleep(WORK_BETWEEN_WRITES_MS);

                send(producer, order);
                log.info("{}: Kafka ✓", name);

                orders++;
                Thread.sleep(PAUSE_BETWEEN_ORDERS_MS);
            }
            log.info("Stopped cleanly after {} orders", orders);
        }
    }

    /** Autocommit is on: once this returns, the row is committed. */
    private static void insert(Connection db, MaintenanceOrder order) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(
                "INSERT INTO maintenance_order (order_id, wind_turbine_id, task, planned_for) VALUES (?, ?, ?, ?)")) {
            stmt.setObject(1, UUID.fromString(order.orderId()));
            stmt.setString(2, order.windTurbineId());
            stmt.setString(3, order.task());
            stmt.setDate(4, Date.valueOf(order.plannedFor()));
            stmt.executeUpdate();
        }
    }

    /** Blocks until the broker has acknowledged the record. */
    private static void send(Producer<String, MaintenanceOrder> producer, MaintenanceOrder order)
            throws ExecutionException, InterruptedException {
        producer.send(new ProducerRecord<>(TOPIC, order.windTurbineId(), order)).get();
    }

    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "maintenance-planning-dualwrite");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    private DualWriteApp() {
    }
}
