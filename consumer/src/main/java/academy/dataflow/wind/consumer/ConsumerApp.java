package academy.dataflow.wind.consumer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads wind turbine telemetry and shows the current power per wind park.
 *
 * <p>Everything outside the TODOs is scaffolding and already works: the
 * overview, the rebalance logging, graceful shutdown. Yours are the
 * configuration, the consumer, the subscription, the poll loop and - in the
 * error handling lab - what happens when a record cannot be read.
 */
public final class ConsumerApp {

    /**
     * The consumer configuration.
     *
     * <p>TODO 1: fill in the missing group. The lab text tells you which
     * questions it answers.
     */
    private static Properties consumerConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        // Makes this consumer identifiable in broker logs, metrics and quotas.
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, hostname());
        // The rebalance protocol with broker-side assignment. Use it from
        // Kafka 4.1 on; anything else is legacy.
        props.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "consumer");
        // Never read records of open or aborted transactions. Costs nothing
        // without transactions, so set it always.
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // Deserialization: the key is a plain string, the value is a
        // WindTurbineMeasurement in JSON. Which deserializers do you need?
        // (The value one comes from io.confluent:kafka-json-serializer and
        // needs no registry. JSON carries no type, so it also needs to be
        // told the target class: KafkaJsonDeserializerConfig.JSON_VALUE_TYPE.)

        // The group: which group does this consumer join (use GROUP_ID), and
        // where does it start when the group has no committed offset yet?

        return props;
    }

    private static final Logger log = LoggerFactory.getLogger(ConsumerApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "nordwind.scada.public.turbine-telemetry.event";
    private static final String GROUP_ID = "turbine-overview";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    private static volatile boolean running = true;

    public static void main(String[] args) {
        ParkOverview overview = new ParkOverview(GROUP_ID);

        // TODO 2: create the consumer and replace both '?' with the right
        // types. They have to match the deserializers from TODO 1.
        Consumer<?, ?> consumer = null; // new KafkaConsumer<>(consumerConfig())

        if (consumer == null) {
            log.error("No consumer yet - TODO 2 is still open. See the lab text.");
            System.exit(1);
        }

        installShutdownHook(consumer);

        try {
            // TODO 3: subscribe to TOPIC. Pass new LoggingRebalanceListener(overview)
            // as the second argument, so you see which partitions this
            // instance gets.

            log.info("Reading '{}' as group '{}' (bootstrap: {})", TOPIC, GROUP_ID, BOOTSTRAP_SERVERS);

            while (running) {
                try {
                    // TODO 4: poll with POLL_TIMEOUT and hand every record to
                    // overview.add(record).
                    //
                    // poll() does more than fetch: it sends heartbeats, takes
                    // part in rebalances and commits the offsets of the
                    // previous poll every 5 seconds.

                } catch (RecordDeserializationException e) {
                    // TODO 5 - in the error handling lab: a record that
                    // cannot be deserialized. Your group's decision goes here.
                    throw e;
                }
                overview.pollDone();
            }
        } catch (WakeupException e) {
            // Thrown by poll() after consumer.wakeup(): the normal way out.
        } finally {
            // close() commits the offsets of what was processed and leaves the
            // group at once, instead of making it wait for a timeout.
            consumer.close();
            log.info("Consumer closed");
        }
    }

    /**
     * Ctrl+C does not kill the JVM on the spot. poll() may be blocking, so
     * the hook calls wakeup(), which makes poll() throw a WakeupException,
     * and waits until main() has closed the consumer.
     */
    private static void installShutdownHook(Consumer<?, ?> consumer) {
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping consumer ...");
            running = false;
            consumer.wakeup();
            try {
                mainThread.join(Duration.ofSeconds(15));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));
    }

    /** Logs which partitions this instance gets and loses. */
    static final class LoggingRebalanceListener implements ConsumerRebalanceListener {
        private final ParkOverview overview;

        LoggingRebalanceListener(ParkOverview overview) {
            this.overview = overview;
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            if (!partitions.isEmpty()) {
                log.info("Partitions assigned: {}", ParkOverview.numbers(partitions));
            }
            overview.assigned(partitions);
        }

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            if (!partitions.isEmpty()) {
                log.info("Partitions revoked: {}", ParkOverview.numbers(partitions));
            }
            overview.revoked(partitions);
        }

        @Override
        public void onPartitionsLost(Collection<TopicPartition> partitions) {
            log.warn("Partitions lost: {}", ParkOverview.numbers(partitions));
            overview.revoked(partitions);
        }
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    private ConsumerApp() {
    }
}
