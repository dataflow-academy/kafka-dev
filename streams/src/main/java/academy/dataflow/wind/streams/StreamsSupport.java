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
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab scaffolding — not part of the exercise. It keeps the lab observable and
 * safe to break; you would not write this in a production client.
 */
final class StreamsSupport {

    private static final Logger log = LoggerFactory.getLogger(StreamsSupport.class);

    private static final AtomicLong processed = new AtomicLong();
    private static volatile long lastReport = System.currentTimeMillis();

    /** Names the client after the machine, so each participant's client is easy to spot. */
    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    /** Ends the run with a pointer to the lab text while TODO 1 is still open. */
    static void exitIfNoGuarantee(Properties config) {
        if (config.get(StreamsConfig.PROCESSING_GUARANTEE_CONFIG) == null) {
            log.error("No processing guarantee set - TODO 1 is still open. See the lab text.");
            System.exit(1);
        }
    }

    /** Ends the run with a pointer to the lab text while TODO 2 or 3 is still open. */
    static void exitIfNotWritingTo(Topology topology, String debitsTopic, String creditsTopic) {
        String description = topology.describe().toString();
        if (!description.contains(debitsTopic) || !description.contains(creditsTopic)) {
            log.error("The topology does not write to both booking topics yet - TODO 2 and 3. See the lab text.");
            System.exit(1);
        }
    }

    /**
     * Writes the topology to topology.txt in the working directory, so it can
     * be opened and pasted into a visualizer. Also logs it.
     */
    static void publishTopology(Topology topology) {
        String description = topology.describe().toString();
        log.info("Topology:\n{}", description);
        Path file = Path.of(System.getProperty("user.dir"), "topology.txt").toAbsolutePath();
        try {
            Files.writeString(file, description);
            log.info("Topology written to {}", file);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", file, e.toString());
        }
    }

    /**
     * Counts the transfers and logs the count every five seconds. Stops the
     * JVM at transfer {@code haltAt} of this run (0 = never), so you can watch
     * what a crash leaves behind.
     */
    static ForeachAction<String, BankTransfer> countAndHaltAt(long haltAt) {
        return (key, transfer) -> {
            long n = processed.incrementAndGet();
            if (n == haltAt) {
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
        };
    }

    /**
     * Starts the topology and blocks until Ctrl+C or Stop, which close Kafka
     * Streams cleanly, or until Streams dies on its own.
     */
    static void runUntilShutdown(KafkaStreams streams) {
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

    private StreamsSupport() {
    }
}
