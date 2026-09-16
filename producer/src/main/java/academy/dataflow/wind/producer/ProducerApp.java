package academy.dataflow.wind.producer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces wind turbine telemetry to Kafka.
 *
 * <p>Everything outside the TODOs is scaffolding and already works: the
 * simulator, the tick loop, graceful shutdown, logging. Four things are
 * yours: the configuration, the producer, the record you send, and what
 * happens when a send fails.
 */
public final class ProducerApp {

    /**
     * The producer configuration.
     *
     * <p>TODO 1: fill this in. The lab text tells you which questions each
     * group of settings answers. For every line you add, know what it costs
     * you - "it is the default" is not an answer.
     */
    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);

        // Serialization: the key is a plain string, the value is a
        // WindTurbineMeasurement. Which serializers do you need?
        // (The value one comes from io.confluent:kafka-json-serializer and
        // needs no registry - it is a thin Jackson wrapper.)

        // Reliability: what has to be true before the broker confirms a write,
        // and what stops a retry from creating a duplicate?

        // Throughput: how long may the producer collect before sending, how
        // much may it collect, and should it compress?

        // Operations: which value makes this producer identifiable in broker
        // logs, metrics and quotas? hostname() at the bottom gives you one.

        return props;
    }

    private static final Logger log = LoggerFactory.getLogger(ProducerApp.class);

    private static final String BOOTSTRAP_SERVERS =
            env("BOOTSTRAP_SERVERS", "localhost:9092,localhost:9093,localhost:9094");
    private static final String TOPIC =
            env("TOPIC", "nordwind.scada.public.turbine-telemetry.event");
    /** How often every turbine reports, in milliseconds. */
    private static final long TICK_INTERVAL_MS = Long.parseLong(env("TICK_INTERVAL_MS", "1000"));

    /** Set by the delivery callback when a send has failed for good. */
    private static final AtomicBoolean fatalError = new AtomicBoolean(false);
    private static volatile boolean running = true;

    public static void main(String[] args) throws InterruptedException {
        // Ctrl+C does not kill the JVM on the spot: the hook below ends the
        // loop and waits until close() has flushed what is still buffered.
        installShutdownHook();

        WindParkSimulator simulator = new WindParkSimulator();
        long produced = 0;
        long startedAt = System.currentTimeMillis();

        // TODO 2: create the producer and replace both '?' with the right
        // types. Look at what you are sending and at the serializers you
        // configured in TODO 1 - they have to match.
        Producer<?, ?> producer = null; // new KafkaProducer<>(producerConfig())

        // try-with-resources: close() flushes everything still buffered.
        // A producer that is not closed loses whatever sits in its batches.
        try (producer) {
            log.info("Producing to '{}' every {} ms (bootstrap: {})",
                    TOPIC, TICK_INTERVAL_MS, BOOTSTRAP_SERVERS);

            while (running && !fatalError.get()) {
                long tickStart = System.currentTimeMillis();

                List<WindTurbineMeasurement> measurements = simulator.nextTick(Instant.now());
                for (WindTurbineMeasurement measurement : measurements) {
                    // TODO 3: build the record and send it.
                    //
                    // a) Which value belongs in the key? Your choice decides
                    //    which measurements land on the same partition - and
                    //    therefore what stays in order. Whatever you pick,
                    //    be able to say why.
                    //
                    // b) send() is asynchronous: it appends to a local batch
                    //    and returns. Do NOT call .get() on the returned
                    //    future per message - that turns every send into a
                    //    network round trip and destroys batching.
                    //
                    // ProducerRecord<?, ?> record = new ProducerRecord<>(TOPIC, ??, measurement);
                    // producer.send(record);
                    //
                    // TODO 4: pass a callback as the second argument to send().
                    // It runs when the broker acknowledged, or when delivery
                    // failed for good - by then the client has already
                    // exhausted its internal retries. What now? The lab text
                    // has three options and one anti-pattern; for "stop",
                    // call giveUp() below.
                    //
                    // producer.send(record, (metadata, exception) -> {
                    //     if (exception != null) {
                    //         ...
                    //     }
                    // });

                    produced++;
                }

                if (produced % (measurements.size() * 30L) == 0) {
                    long seconds = Math.max(1, (System.currentTimeMillis() - startedAt) / 1000);
                    log.info("Produced {} measurements so far ({} msg/s)", produced, produced / seconds);
                }

                // Keep a steady tick rate regardless of how long sending took.
                long sleep = TICK_INTERVAL_MS - (System.currentTimeMillis() - tickStart);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        }

        if (fatalError.get()) {
            log.error("Exiting due to a fatal delivery error (see log above)");
            System.exit(1);
        }
        log.info("Producer stopped cleanly after {} measurements", produced);
    }

    private static void installShutdownHook() {
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping producer ...");
            running = false;
            try {
                mainThread.join(Duration.ofSeconds(15).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));
    }

    /**
     * Call this from the failure branch of your delivery callback to stop the
     * main loop and exit non-zero.
     *
     * <p>Why a flag instead of System.exit() inside the callback: the callback
     * runs on the producer's single I/O thread. exit() would block that thread
     * while shutdown hooks run - and close() waits for exactly that thread to
     * drain. Deadlock.
     */
    static void giveUp() {
        fatalError.set(true);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private ProducerApp() {
    }
}
