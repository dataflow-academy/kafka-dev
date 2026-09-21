package academy.dataflow.wind.producer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab scaffolding, not part of the exercise. It keeps the lab observable and
 * safe to break; a production client would not need any of it.
 */
final class ProducerSupport {

    private static final Logger log = LoggerFactory.getLogger(ProducerApp.class);

    private static final long MEASURE_SECONDS = 120;
    private static final long REPORT_INTERVAL_MS = 5000;

    /** Set by giveUp() when a send has failed for good. */
    private static final AtomicBoolean fatalError = new AtomicBoolean(false);
    /** Counted down once the producer is closed. */
    private static final CountDownLatch stopped = new CountDownLatch(1);

    private static long startedAt;
    private static long lastReport;
    private static double lastRecords;
    private static double lastRetries;
    /** JSON value of an average measurement, before compression. */
    private static double valueBytes;

    /** Stops with a hint while TODO 2 has not created the producer yet. */
    static void exitIfMissing(Producer<?, ?> producer) {
        if (producer == null) {
            log.error("No producer yet - TODO 2 is still open. See the lab text.");
            System.exit(1);
        }
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

    /** True once giveUp() has been called. */
    static boolean gaveUp() {
        return fatalError.get();
    }

    /** Starts the clock for the reports and measures how large a record value is. */
    static void startStats(WindParkSimulator simulator) {
        startedAt = System.currentTimeMillis();
        lastReport = startedAt;
        valueBytes = averageValueBytes(simulator.nextTick(Instant.now()));
    }

    /** True when a measurement run (tick interval 0) has used up its MEASURE_SECONDS. */
    static boolean measurementOver(long tickIntervalMs) {
        return tickIntervalMs == 0 && System.currentTimeMillis() - startedAt >= MEASURE_SECONDS * 1000;
    }

    /**
     * Every five seconds: throughput since the last report and latency of the
     * last 30 to 60 seconds, in the style of kafka-producer-perf-test. The
     * numbers come from the producer's own metrics, so they count what the
     * brokers acknowledged, not what was handed to send().
     */
    static void maybeLogProgress(Producer<?, ?> producer) {
        long now = System.currentTimeMillis();
        if (now - lastReport < REPORT_INTERVAL_MS) {
            return;
        }
        double seconds = (now - lastReport) / 1000.0;
        lastReport = now;

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

    /** Logs throughput and batch statistics of the whole run. */
    static void logSummary(Producer<?, ?> producer) {
        double seconds = Math.max(1, System.currentTimeMillis() - startedAt) / 1000.0;
        double records = acknowledged(producer);
        log.info(String.format("%.0f records sent in %.0f s, %.0f records/sec (%.2f MB/sec)",
                records, seconds, records / seconds,
                records / seconds * valueBytes / 1_000_000));
        log.info(String.format("batches: avg %.0f bytes, %.1f records per request, compressed to %.0f %%",
                metric(producer, "batch-size-avg"),
                metric(producer, "records-per-request-avg"),
                metric(producer, "compression-rate-avg") * 100));
    }

    /**
     * Installs a shutdown hook for Ctrl+C: it logs, runs {@code stop} and waits
     * until {@link #closed()} says the producer has sent what was buffered.
     */
    static void onShutdown(Runnable stop) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped.getCount() == 0) {
                return; // the loop has already ended, e.g. after giveUp()
            }
            log.info("Shutdown signal received, stopping producer ...");
            stop.run();
            try {
                stopped.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));
    }

    /** Tells the shutdown hook that the producer is closed. */
    static void closed() {
        stopped.countDown();
    }

    /** Ends the run: exit code 1 after giveUp(), a closing line otherwise. */
    static void exit(long produced) {
        if (fatalError.get()) {
            log.error("Exiting due to a fatal delivery error (see log above)");
            System.exit(1);
        }
        log.info("Producer stopped cleanly after {} measurements", produced);
    }

    /** The host name, used as client.id. */
    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
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

    private ProducerSupport() {
    }
}
