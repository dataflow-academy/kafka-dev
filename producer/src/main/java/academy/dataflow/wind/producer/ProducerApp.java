package academy.dataflow.wind.producer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
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
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        // Makes this producer identifiable in broker logs, metrics and quotas.
        props.put(ProducerConfig.CLIENT_ID_CONFIG, hostname());

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
    /**
     * How often every turbine reports, in milliseconds. 0 removes the brake
     * and turns a run into a measurement that stops after MEASURE_SECONDS.
     */
    private static final long TICK_INTERVAL_MS = 1000;
    private static final long MEASURE_SECONDS = 120;

    /** Set by the delivery callback when a send has failed for good. */
    private static final AtomicBoolean fatalError = new AtomicBoolean(false);
    private static volatile boolean running = true;
    /** Counted down once the producer is closed. */
    private static final CountDownLatch stopped = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        WindParkSimulator simulator = new WindParkSimulator();
        long produced = 0;
        long startedAt = System.currentTimeMillis();
        long lastReport = startedAt;
        valueBytes = averageValueBytes(simulator.nextTick(Instant.now()));

        // TODO 2: create the producer and replace both '?' with the right
        // types. Look at what you are sending and at the serializers you
        // configured in TODO 1 - they have to match.
        Producer<?, ?> producer = null; // new KafkaProducer<>(producerConfig())

        if (producer == null) {
            log.error("No producer yet - TODO 2 is still open. See the lab text.");
            System.exit(1);
        }

        // Ctrl+C does not kill the JVM on the spot: the hook below ends the
        // loop and waits until close() has sent what is still buffered.
        installShutdownHook();

        try {
            log.info("Producing to '{}' every {} ms (bootstrap: {})",
                    TOPIC, TICK_INTERVAL_MS, BOOTSTRAP_SERVERS);

            while (running && !fatalError.get()) {
                long tickStart = System.currentTimeMillis();
                if (TICK_INTERVAL_MS == 0 && tickStart - startedAt >= MEASURE_SECONDS * 1000) {
                    break;
                }

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
                    // anti-pattern; for "stop", call giveUp(exception) below.
                    //
                    // producer.send(record, (metadata, exception) -> {
                    //     if (exception != null) {
                    //         ...
                    //     }
                    // });

                    produced++;
                }

                long now = System.currentTimeMillis();
                if (now - lastReport >= 5000) {
                    logProgress(producer, (now - lastReport) / 1000.0);
                    lastReport = now;
                }

                // Keep a steady tick rate regardless of how long sending took.
                long sleep = TICK_INTERVAL_MS - (System.currentTimeMillis() - tickStart);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }

            if (!fatalError.get()) {
                if (running) {
                    // A measurement run ended: flush, so the summary covers
                    // every record, not just the ones acknowledged so far.
                    producer.flush();
                }
                logSummary(producer, System.currentTimeMillis() - startedAt);
            }
        } finally {
            // A producer that is not closed loses whatever sits in its batches.
            // After giveUp() there is nothing worth waiting for: close at once
            // and let the remaining sends fail. Otherwise give them 10 s.
            producer.close(fatalError.get() ? Duration.ZERO : Duration.ofSeconds(10));
        }
        stopped.countDown();

        if (fatalError.get()) {
            log.error("Exiting due to a fatal delivery error (see log above)");
            System.exit(1);
        }
        log.info("Producer stopped cleanly after {} measurements", produced);
    }

    private static double lastRecords;
    private static double lastRetries;
    /** JSON value of an average measurement, before compression. */
    private static double valueBytes;

    /**
     * Throughput since the last report and latency of the last 30 to 60
     * seconds, in the style of kafka-producer-perf-test. The numbers come from
     * the producer's own metrics, so they count what the brokers acknowledged,
     * not what was handed to send().
     */
    private static void logProgress(Producer<?, ?> producer, double seconds) {
        double records = acknowledged(producer);
        double retries = metric(producer, "record-retry-total");
        double recordsPerSec = Math.max(0, records - lastRecords) / seconds;
        double queued = metric(producer, "record-queue-time-avg");
        double request = metric(producer, "request-latency-avg");
        String line = String.format("%.0f records/sec (%.2f MB/sec), latency avg %.1f ms (%.1f queued + %.1f request), max %.0f ms",
                recordsPerSec,
                recordsPerSec * valueBytes / 1_000_000,
                queued + request, queued, request,
                metric(producer, "record-queue-time-max") + metric(producer, "request-latency-max"));
        // The client logs every retry at WARN; logback.xml silences that, so
        // they show up here instead.
        if (retries > lastRetries) {
            line += String.format(", %.0f retries/sec", (retries - lastRetries) / seconds);
        }
        log.info(line);
        lastRecords = records;
        lastRetries = retries;
    }

    private static void logSummary(Producer<?, ?> producer, long elapsedMs) {
        double seconds = Math.max(1, elapsedMs) / 1000.0;
        double records = acknowledged(producer);
        log.info(String.format("%.0f records sent in %.0f s, %.0f records/sec (%.2f MB/sec)",
                records, seconds, records / seconds,
                records / seconds * valueBytes / 1_000_000));
        log.info(String.format("batches: avg %.0f bytes, %.1f records per request, compressed to %.0f %%",
                metric(producer, "batch-size-avg"),
                metric(producer, "records-per-request-avg"),
                metric(producer, "compression-rate-avg") * 100));
    }

    private static double averageValueBytes(List<WindTurbineMeasurement> sample) {
        ObjectMapper mapper = new ObjectMapper();
        long total = 0;
        for (WindTurbineMeasurement m : sample) {
            try {
                total += mapper.writeValueAsBytes(m).length;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return (double) total / sample.size();
    }

    /** Every send attempt ends acknowledged, retried or failed. */
    private static double acknowledged(Producer<?, ?> producer) {
        return metric(producer, "record-send-total")
                - metric(producer, "record-retry-total")
                - metric(producer, "record-error-total");
    }

    private static double metric(Producer<?, ?> producer, String name) {
        for (var entry : producer.metrics().entrySet()) {
            if (entry.getKey().group().equals("producer-metrics") && entry.getKey().name().equals(name)) {
                Object value = entry.getValue().metricValue();
                return value instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : 0;
            }
        }
        return 0;
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
     * main loop and exit non-zero. Only the first failure is logged: once the
     * cluster is gone, every buffered record fails the same way.
     *
     * <p>Why a flag instead of System.exit() inside the callback: the callback
     * runs on the producer's single I/O thread. exit() would block that thread
     * while shutdown hooks run - and close() waits for exactly that thread to
     * drain. Deadlock.
     */
    static void giveUp(Exception cause) {
        if (fatalError.compareAndSet(false, true)) {
            log.error("Giving up after a failed send: {}", cause.toString());
        }
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
