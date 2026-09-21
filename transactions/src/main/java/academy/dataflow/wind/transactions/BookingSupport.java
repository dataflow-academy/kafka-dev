package academy.dataflow.wind.transactions;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab scaffolding — not part of the exercise. It keeps the lab observable and
 * safe to break; you would not write this in a production client.
 */
final class BookingSupport {

    private static final Logger log = LoggerFactory.getLogger(BookingSupport.class);

    /** The lab cluster: three brokers on this machine. */
    static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";

    private static final AtomicLong started = new AtomicLong();

    /**
     * Stops the JVM at the configured transfer, between the two bookings, so
     * you can watch what a crash leaves behind.
     *
     * <p>{@code Runtime.halt()} skips shutdown hooks and finally blocks:
     * nothing gets closed, committed or aborted. Just like a power cut.
     *
     * @param haltAt transfer number of this run, 0 = never
     */
    static void haltIfDue(long haltAt, Producer<?, ?> producer, BankTransfer transfer) {
        if (started.incrementAndGet() != haltAt) {
            return;
        }
        // Make sure the first booking really reached the broker; otherwise the
        // halt would just discard it from the producer's buffer.
        producer.flush();
        log.warn("HALT while booking transfer #{} of this run ({}): the first booking is written, "
                + "the second is not. Set HALT_AT_TRANSFER back to 0 before the next start.",
                haltAt, transfer.transferId());
        Runtime.getRuntime().halt(1);
    }

    /** Names the client after the machine, so each participant's client is easy to spot. */
    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    /** Waits up to 15 s for the given thread to finish, so it can close its clients. */
    static void awaitExit(Thread thread) {
        try {
            thread.join(15_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Ends the run with a pointer to the lab text while TODO 1 or 2 of TransactionalApp is still open. */
    static void exitIfTransactionTodosOpen(Properties producerConfig, Properties consumerConfig) {
        if (producerConfig.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG) == null) {
            log.error("The producer has no transactional id - TODO 1 is still open. See the lab text.");
            System.exit(1);
        }
        if (!"false".equals(Objects.toString(consumerConfig.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)))) {
            log.error("The consumer still commits on its own - TODO 2 is still open. See the lab text.");
            System.exit(1);
        }
        if (!"read_committed".equals(Objects.toString(consumerConfig.get(ConsumerConfig.ISOLATION_LEVEL_CONFIG)))) {
            log.error("The consumer would also read transfers from aborted transactions - TODO 2 is still open. "
                    + "See the lab text.");
            System.exit(1);
        }
    }

    /** Ends the run with a pointer to the lab text if the topic is missing, instead of letting the broker create it. */
    static void exitIfTopicMissing(String topic) throws Exception {
        // The broker would create the topic on the first send, with one
        // partition and no replicas. Better to stop and say so.
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS))) {
            admin.describeTopics(List.of(topic)).allTopicNames().get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                log.error("The topic '{}' does not exist. Create it first, see the lab text.", topic);
                System.exit(1);
            }
            throw e;
        }
    }

    /**
     * Makes up the transfers for TransferSource: random accounts, mostly small
     * amounts, now and then a large one.
     */
    static final class TransferGenerator {
        private static final List<String> ACCOUNTS = List.of(
                "alice", "bob", "carol", "dave", "erin", "frank",
                "grace", "heidi", "ivan", "judy", "mallory", "oscar");

        private final Random random = new Random();
        private final String run;
        private int count;
        private long totalCents;

        /** Every transfer id contains {@code run}, so each run's ids are fresh. */
        TransferGenerator(String run) {
            this.run = run;
        }

        /** The next transfer, with the id {@code tx-<run>-<number>}. */
        BankTransfer next() {
            String from = ACCOUNTS.get(random.nextInt(ACCOUNTS.size()));
            String to = from;
            while (to.equals(from)) {
                to = ACCOUNTS.get(random.nextInt(ACCOUNTS.size()));
            }
            long amountCents = random.nextInt(10) == 0
                    ? 500_000 + random.nextInt(1_000_000)
                    : 100 + random.nextInt(20_000);
            count++;
            totalCents += amountCents;
            return new BankTransfer(
                    String.format("tx-%s-%04d", run, count), from, to, amountCents, System.currentTimeMillis());
        }

        /** The sum of all amounts so far, so the bookings can be checked against it. */
        long totalCents() {
            return totalCents;
        }
    }

    /** Logs how many transfers were booked: at most every five seconds, and once caught up. */
    static final class Progress {
        private long booked;
        private long reported;
        private long lastReport = System.currentTimeMillis();
        private boolean idleReported = true;

        /** Counts one booked transfer. */
        void countBooked() {
            booked++;
            idleReported = false;
            long now = System.currentTimeMillis();
            if (now - lastReport >= 5000) {
                log.info("{} transfers booked so far", booked);
                reported = booked;
                lastReport = now;
            }
        }

        /** Call after every poll. Returns true after the first empty poll that follows new work. */
        boolean afterPoll(ConsumerRecords<?, ?> records) {
            if (!records.isEmpty() || idleReported) {
                return false;
            }
            idleReported = true;
            if (booked != reported) {
                log.info("{} transfers booked so far - all caught up, waiting for more", booked);
                reported = booked;
                lastReport = System.currentTimeMillis();
            }
            return true;
        }

        /** How many transfers this run has booked. */
        long total() {
            return booked;
        }
    }

    /**
     * Stops AtLeastOnceApp from looking like it works while its TODO 1 is
     * still open: transfers were read, but no booking was sent.
     */
    static final class OpenTodoGuard {
        private final Consumer<?, ?> consumer;
        private final Producer<?, ?> producer;
        private final Progress progress;
        private Map<TopicPartition, OffsetAndMetadata> startOffsets = Map.of();

        OpenTodoGuard(Consumer<?, ?> consumer, Producer<?, ?> producer, Progress progress) {
            this.consumer = consumer;
            this.producer = producer;
            this.progress = progress;
        }

        /**
         * Call after every poll; it also logs the progress. Returns true if
         * TODO 1 is still open. The group is then back where this run began,
         * so no transfer is lost.
         */
        boolean todoStillOpen(ConsumerRecords<?, ?> records) {
            if (startOffsets.isEmpty() && !consumer.assignment().isEmpty()) {
                startOffsets = offsetsAtStart();
            }
            if (!progress.afterPoll(records)) {
                return false;
            }
            producer.flush();
            double sent = producer.metrics().entrySet().stream()
                    .filter(e -> e.getKey().group().equals("producer-metrics")
                            && e.getKey().name().equals("record-send-total"))
                    .mapToDouble(e -> ((Number) e.getValue().metricValue()).doubleValue())
                    .sum();
            long booked = progress.total();
            if (booked == 0 || sent > 0) {
                return false;
            }
            log.error("{} transfers read, but not a single booking sent - TODO 1 is still open. See the lab text.",
                    booked);
            // The automatic commit may already have moved the group forward, and
            // close() would move it further. Put the consumer back where this run
            // began, otherwise these transfers would be gone for good.
            startOffsets.forEach((partition, offset) -> consumer.seek(partition, offset.offset()));
            consumer.commitSync(startOffsets);
            return true;
        }

        /** Where the group stood when this run began, so an aborted run can put it back. */
        private Map<TopicPartition, OffsetAndMetadata> offsetsAtStart() {
            Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>(consumer.committed(consumer.assignment()));
            Map<TopicPartition, Long> beginning = consumer.beginningOffsets(consumer.assignment());
            committed.replaceAll((partition, offset) ->
                    offset != null ? offset : new OffsetAndMetadata(beginning.get(partition)));
            return committed;
        }
    }

    private BookingSupport() {
    }
}
