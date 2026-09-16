package academy.dataflow.wind.producer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
     * <p>TODO 1: fill this in, one group per lab. The lab text tells you
     * which questions each group of settings answers.
     */
    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);
        // Makes this producer identifiable in broker logs, metrics and quotas.
        props.put("client.id", hostname());

        // Serialization: the key is a plain string, the value is a
        // WindTurbineMeasurement. Which serializers do you need?
        // (The value one comes from io.confluent:kafka-json-serializer and
        // needs no registry - it is a thin Jackson wrapper.)

        // Reliability - we get to this in the reliability lab: what has to be
        // true before the broker confirms a write, and what stops a retry from
        // creating a duplicate?

        // Throughput - we get to this in the performance lab: how long may the
        // producer collect before sending, how much may it collect, and should
        // it compress?

        return props;
    }

    private static final Logger log = LoggerFactory.getLogger(ProducerApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "nordwind.scada.public.turbine-telemetry.event";
    /** How often every turbine reports, in milliseconds. 0 removes the brake. */
    private static final long TICK_INTERVAL_MS = 1000;

    /** Set by the delivery callback when a send has failed for good. */
    private static final AtomicBoolean fatalError = new AtomicBoolean(false);
    private static volatile boolean running = true;
    /** Counted down once the producer is closed. */
    private static final CountDownLatch stopped = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        WindParkSimulator simulator = new WindParkSimulator();
        long produced = 0;
        long startedAt = System.currentTimeMillis();

        // TODO 2: create the producer and replace both '?' with the right
        // types. Look at what you are sending and at the serializers you
        // configured in TODO 1 - they have to match.
        Producer<?, ?> producer = null; // new KafkaProducer<>(producerConfig())

        // Delete this guard once TODO 2 is done. Without it the loop would run
        // and report progress while nothing reaches Kafka.
        if (producer == null) {
            log.error("No producer yet - TODO 2 is still open. See the lab text.");
            System.exit(1);
        }

        // Ctrl+C does not kill the JVM on the spot: the hook below ends the
        // loop and waits until close() has flushed what is still buffered.
        installShutdownHook();

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
                    // TODO 4 - in the reliability lab: pass a callback as the
                    // second argument to send(). It runs when the broker
                    // acknowledged, or when delivery failed for good - by then
                    // the client has already exhausted its internal retries.
                    // What now? The lab text has three options and one
                    // anti-pattern; for "stop", call giveUp() below.
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
        stopped.countDown();

        if (fatalError.get()) {
            log.error("Exiting due to a fatal delivery error (see log above)");
            System.exit(1);
        }
        log.info("Producer stopped cleanly after {} measurements", produced);
    }

    private static void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped.getCount() == 0) {
                return; // the loop has already ended, e.g. after giveUp()
            }
            log.info("Shutdown signal received, stopping producer ...");
            running = false;
            try {
                stopped.await(15, TimeUnit.SECONDS);
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

    private ProducerApp() {
    }
}
