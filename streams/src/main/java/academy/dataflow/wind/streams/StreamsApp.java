package academy.dataflow.wind.streams;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Books transfers with Kafka Streams: the same job as the transactional app,
 * without writing the transaction yourself.
 *
 * <p>Everything outside the TODOs is scaffolding and already works.
 */
public final class StreamsApp {

    private static final Logger log = LoggerFactory.getLogger(StreamsApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    /** All three bank labs read from this one topic; TransferSource fills it. */
    private static final String TRANSFERS_TOPIC = "nordbank.payments.public.transfer.event";
    /** The bookings stay per lab, so the last lab's result stays readable. */
    private static final String DEBITS_TOPIC = "nordbank.payments.public.debit-streams.event";
    private static final String CREDITS_TOPIC = "nordbank.payments.public.credit-streams.event";
    /** Also the consumer group and the transactional id prefix. */
    private static final String APPLICATION_ID = "nordbank-booking-streams";
    /**
     * The process dies while it processes this transfer (counted from the
     * start of this run). 0 = never.
     */
    private static final long HALT_AT_TRANSFER = 0;
    /** Where the local state stores live, one subdirectory per application.id. */
    private static final String STATE_DIR = System.getProperty("user.home") + "/kafka-streams";

    /** TODO 1: the processing guarantee. */
    private static Properties streamsConfig() {
        Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        props.put(StreamsConfig.CLIENT_ID_CONFIG, hostname());
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
        props.put(StreamsConfig.STATE_DIR_CONFIG, STATE_DIR);
        // Costs nothing and saves a surprise: without it a consumer reads
        // records from aborted transactions too.
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.ISOLATION_LEVEL_CONFIG), "read_committed");

        // TODO 1: a debit without its credit must never become visible.
        // One setting.

        return props;
    }

    private static Topology buildTopology(Properties config) {
        Serde<String> keySerde = Serdes.String();
        Serde<Booking> bookingSerde = JsonSerde.of(Booking.class);

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, BankTransfer> transfers = builder
                .stream(TRANSFERS_TOPIC, Consumed.with(keySerde, JsonSerde.of(BankTransfer.class)))
                // The process dies here at HALT_AT_TRANSFER.
                .peek(StreamsApp::countAndHaltIfDue);

        // TODO 2: the debits. Build a Booking for the paying account out of
        // every transfer and write it to DEBITS_TOPIC with
        // Produced.with(keySerde, bookingSerde). The transfer's key already is
        // the paying account.

        // TODO 3: the credits, keyed by the receiving account.

        return builder.build(config);
    }

    public static void main(String[] args) {
        Properties config = streamsConfig();
        if (config.get(StreamsConfig.PROCESSING_GUARANTEE_CONFIG) == null) {
            log.error("No processing guarantee set - TODO 1 is still open. See the lab text.");
            System.exit(1);
        }
        Topology topology = buildTopology(config);
        String description = topology.describe().toString();
        publishTopology(description);
        if (!description.contains(DEBITS_TOPIC) || !description.contains(CREDITS_TOPIC)) {
            log.error("The topology does not write to both booking topics yet - TODO 2 and 3. See the lab text.");
            System.exit(1);
        }
        log.info("Processing guarantee: {}, halt at transfer: {}",
                config.get(StreamsConfig.PROCESSING_GUARANTEE_CONFIG),
                HALT_AT_TRANSFER == 0 ? "never" : HALT_AT_TRANSFER);
        runUntilShutdown(new KafkaStreams(topology, config));
    }

    /**
     * Writes the topology to topology.txt in the working directory, so it can
     * be opened and pasted into a visualizer. Also logs it.
     */
    private static void publishTopology(String description) {
        log.info("Topology:\n{}", description);
        Path file = Path.of(System.getProperty("user.dir"), "topology.txt").toAbsolutePath();
        try {
            Files.writeString(file, description);
            log.info("Topology written to {}", file);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static final AtomicLong processed = new AtomicLong();
    private static volatile long lastReport = System.currentTimeMillis();

    private static void countAndHaltIfDue(String key, BankTransfer transfer) {
        long n = processed.incrementAndGet();
        if (n == HALT_AT_TRANSFER) {
            log.warn("HALT while processing transfer #{} of this run ({}). "
                    + "Set HALT_AT_TRANSFER back to 0 before the next start.", n, transfer.transferId());
            try {
                // The crash has to hit an empty producer buffer, not a half
                // full one: wait longer than linger.ms (100 ms in Streams) so
                // that every booking made so far has really reached the
                // broker. Without it the counts afterwards are pure chance.
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(1);
        }
        long now = System.currentTimeMillis();
        if (now - lastReport >= 5000) {
            log.info("{} transfers processed so far", n);
            lastReport = now;
        }
    }

    /** Starts the topology and blocks until Ctrl+C or until Streams dies. */
    private static void runUntilShutdown(KafkaStreams streams) {
        streams.setUncaughtExceptionHandler(exception -> {
            log.error("Uncaught exception in a stream thread - shutting down", exception);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });
        CountDownLatch stopped = new CountDownLatch(1);
        streams.setStateListener((newState, oldState) -> {
            log.info("State {} -> {}", oldState, newState);
            if (newState == KafkaStreams.State.ERROR || newState == KafkaStreams.State.NOT_RUNNING) {
                stopped.countDown();
            }
        });
        CountDownLatch shutdownRequested = new CountDownLatch(1);
        // Ctrl+C and Stop end up here. The hook does the whole shutdown and
        // never waits for the main thread: during shutdown the JVM does not
        // schedule it any more, so joining it would just burn the timeout.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped.getCount() == 0) {
                return; // Streams has already stopped on its own
            }
            log.info("Shutdown signal received, closing Kafka Streams ...");
            shutdownRequested.countDown();
            streams.close(Duration.ofSeconds(30));
            log.info("Stopped after processing {} transfers", processed.get());
            stopped.countDown();
        }, "shutdown-hook"));

        streams.start();
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (shutdownRequested.getCount() > 0) {
            log.error("Kafka Streams stopped on its own (state: {})", streams.state());
            System.exit(1);
        }
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    private StreamsApp() {
    }
}
