package academy.dataflow.wind.consumer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab scaffolding, not part of the exercise. It keeps the lab observable and
 * safe to break; a production client would not need any of it.
 */
final class ConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(ConsumerApp.class);

    /** Counted down once the consumer is closed. */
    private static final CountDownLatch stopped = new CountDownLatch(1);
    private static boolean todoOpen = false;

    /** Stops with a hint while TODO 2 has not created the consumer yet. */
    static void exitIfMissing(Consumer<?, ?> consumer) {
        if (consumer == null) {
            log.error("No consumer yet - TODO 2 is still open. See the lab text.");
            System.exit(1);
        }
    }

    /** True once TODO 3 has subscribed; otherwise logs a hint and remembers it for exit. */
    static boolean subscribed(Consumer<?, ?> consumer) {
        if (consumer.subscription().isEmpty()) {
            log.error("Not subscribed yet - TODO 3 is still open. See the lab text.");
            todoOpen = true;
            return false;
        }
        return true;
    }

    /** Logs a hint when TODO 4 never called poll(), so the run does not look like a success. */
    static void checkPolled(Consumer<?, ?> consumer) {
        if (!todoOpen && !hasPolled(consumer)) {
            log.error("No poll() yet - TODO 4 is still open. See the lab text.");
            todoOpen = true;
        }
    }

    /** Exits with code 1 if one of the checks above found an open TODO. */
    static void exitIfTodoOpen() {
        if (todoOpen) {
            System.exit(1);
        }
    }

    /**
     * Installs a shutdown hook for Ctrl+C: it logs, runs {@code stop} and waits
     * until {@link #closed()} says main() has closed the consumer.
     */
    static void onShutdown(Runnable stop) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped.getCount() == 0) {
                return; // main() has already ended, e.g. after an exception
            }
            log.info("Shutdown signal received, stopping consumer ...");
            stop.run();
            try {
                stopped.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));
    }

    /** Tells the shutdown hook that the consumer is closed. */
    static void closed() {
        stopped.countDown();
    }

    /** The host name, used as client.id. */
    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    /** True once poll() has been called: the metric is -1 before that. */
    private static boolean hasPolled(Consumer<?, ?> consumer) {
        return consumer.metrics().entrySet().stream()
                .filter(e -> e.getKey().name().equals("last-poll-seconds-ago"))
                .anyMatch(e -> ((Number) e.getValue().metricValue()).doubleValue() >= 0);
    }

    /** Logs which partitions this instance gets and loses, and tells the overview. */
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

    private ConsumerSupport() {
    }
}
