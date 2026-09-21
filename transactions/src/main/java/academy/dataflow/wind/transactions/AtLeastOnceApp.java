package academy.dataflow.wind.transactions;

import static academy.dataflow.wind.transactions.BookingSupport.BOOTSTRAP_SERVERS;
import static academy.dataflow.wind.transactions.BookingSupport.haltIfDue;
import static academy.dataflow.wind.transactions.BookingSupport.hostname;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Books transfers by hand: reads each transfer, writes a debit and a credit.
 * The consumer commits the offsets in the background, so a crash between the
 * two bookings replays everything since the last automatic commit.
 *
 * <p>Everything outside the TODO is scaffolding and already works.
 */
public final class AtLeastOnceApp {

    private static final Logger log = LoggerFactory.getLogger(AtLeastOnceApp.class);

    /** All three bank labs read from this one topic; TransferSource fills it. */
    private static final String TRANSFERS_TOPIC = "nordbank.payments.public.transfer.event";
    /** The bookings stay per lab, so this lab's result stays readable afterwards. */
    private static final String DEBITS_TOPIC = "nordbank.payments.public.debit-at-least-once.event";
    private static final String CREDITS_TOPIC = "nordbank.payments.public.credit-at-least-once.event";

    private static final String GROUP_ID = "nordbank-booking-at-least-once";
    /**
     * The process dies while it books this transfer (counted from the start
     * of this run), between the first and the second booking. 0 = never.
     */
    private static final long HALT_AT_TRANSFER = 0;

    private static Properties consumerConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, hostname());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonDeserializer.class);
        props.put(KafkaJsonDeserializerConfig.JSON_VALUE_TYPE, BankTransfer.class.getName());
        // Costs nothing and saves a surprise as soon as somebody upstream
        // writes transactionally. The default is read_uncommitted.
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        // enable.auto.commit stays on its default: the consumer commits the
        // offsets of the last poll in the background, every
        // auto.commit.interval.ms.

        return props;
    }

    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, hostname());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);
        return props;
    }

    private static volatile boolean running = true;
    /** Counts down once the loop has ended, however it ended. */
    private static final CountDownLatch stopped = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        Consumer<String, BankTransfer> consumer = new KafkaConsumer<>(consumerConfig());
        Producer<String, Booking> producer = new KafkaProducer<>(producerConfig());
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!running || stopped.getCount() == 0) {
                return; // the app is exiting on its own, e.g. after an error
            }
            log.info("Shutdown signal received, stopping ...");
            running = false;
            consumer.wakeup();
            try {
                main.join(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));

        BookingSupport.Progress progress = new BookingSupport.Progress();
        boolean todoOpen = false;
        Map<TopicPartition, OffsetAndMetadata> startOffsets = Map.of();
        try (consumer; producer) {
            consumer.subscribe(List.of(TRANSFERS_TOPIC));
            log.info("Booking '{}' -> '{}' and '{}' (group: {}, halt at transfer: {})",
                    TRANSFERS_TOPIC, DEBITS_TOPIC, CREDITS_TOPIC, GROUP_ID,
                    HALT_AT_TRANSFER == 0 ? "never" : HALT_AT_TRANSFER);

            while (running) {
                ConsumerRecords<String, BankTransfer> records = consumer.poll(Duration.ofMillis(500));
                if (startOffsets.isEmpty() && !consumer.assignment().isEmpty()) {
                    startOffsets = offsetsAtStart(consumer);
                }
                if (records.isEmpty()) {
                    if (progress.idle() && !checkScaffold(consumer, producer, progress.total(), startOffsets)) {
                        todoOpen = true;
                        break;
                    }
                    continue;
                }
                for (ConsumerRecord<String, BankTransfer> record : records) {
                    if (!running) {
                        break; // shutdown: the rest of this poll is read again next time
                    }
                    BankTransfer transfer = record.value();
                    Booking debit = Booking.debit(transfer);
                    Booking credit = Booking.credit(transfer);

                    // TODO 1: the first booking. Debit or credit first -
                    // your group decides. Key every booking by its account.

                    // The process dies here at HALT_AT_TRANSFER. Keep this
                    // line between the two bookings.
                    haltIfDue(HALT_AT_TRANSFER, producer, transfer);

                    // TODO 1: the second booking.

                    progress.countBooked();
                }
            }
        } catch (WakeupException e) {
            // shutdown
        } finally {
            stopped.countDown();
        }
        if (todoOpen) {
            // Only now: close() above has left the group, so the next start
            // does not wait for this consumer's session to time out.
            System.exit(1);
        }

        log.info("Stopped after booking {} transfers", progress.total());
    }

    /** Where the group stood when this run began, so an aborted run can put it back. */
    private static Map<TopicPartition, OffsetAndMetadata> offsetsAtStart(Consumer<?, ?> consumer) {
        Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>(consumer.committed(consumer.assignment()));
        Map<TopicPartition, Long> beginning = consumer.beginningOffsets(consumer.assignment());
        committed.replaceAll((partition, offset) -> offset != null ? offset : new OffsetAndMetadata(beginning.get(partition)));
        return committed;
    }

    /** Stops the scaffold from looking like it works while the TODO is open. Returns false if it is. */
    private static boolean checkScaffold(Consumer<?, ?> consumer, Producer<?, ?> producer, long booked,
            Map<TopicPartition, OffsetAndMetadata> startOffsets) {
        producer.flush();
        double sent = producer.metrics().entrySet().stream()
                .filter(e -> e.getKey().group().equals("producer-metrics")
                        && e.getKey().name().equals("record-send-total"))
                .mapToDouble(e -> ((Number) e.getValue().metricValue()).doubleValue())
                .sum();
        if (booked == 0 || sent > 0) {
            return true;
        }
        log.error("{} transfers read, but not a single booking sent - TODO 1 is still open. See the lab text.",
                booked);
        // The automatic commit may already have moved the group forward, and
        // close() would move it further. Put the consumer back where this run
        // began, otherwise these transfers would be gone for good.
        startOffsets.forEach((partition, offset) -> consumer.seek(partition, offset.offset()));
        consumer.commitSync(startOffsets);
        return false;
    }

    private AtLeastOnceApp() {
    }
}
