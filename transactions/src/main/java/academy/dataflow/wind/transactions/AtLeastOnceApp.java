package academy.dataflow.wind.transactions;

import static academy.dataflow.wind.transactions.BookingSupport.BOOTSTRAP_SERVERS;
import static academy.dataflow.wind.transactions.BookingSupport.CREDITS_TOPIC;
import static academy.dataflow.wind.transactions.BookingSupport.DEBITS_TOPIC;
import static academy.dataflow.wind.transactions.BookingSupport.TRANSFERS_TOPIC;
import static academy.dataflow.wind.transactions.BookingSupport.haltIfDue;
import static academy.dataflow.wind.transactions.BookingSupport.hostname;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
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
 * Books transfers by hand: reads each transfer, writes a debit and a credit,
 * commits the offset. At-least-once - or not, depending on where the commit
 * goes.
 *
 * <p>Everything outside the TODOs is scaffolding and already works.
 */
public final class AtLeastOnceApp {

    private static final Logger log = LoggerFactory.getLogger(AtLeastOnceApp.class);

    private static final String GROUP_ID = "nordbank-booking-at-least-once";
    /**
     * The process dies while it books this transfer (counted from the start
     * of this run), between the first and the second booking. 0 = never.
     */
    private static final long HALT_AT_TRANSFER = 0;

    /** TODO 1: who commits the offsets? */
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

        // TODO 1: by default the consumer commits on its own, every few
        // seconds, whatever it has polled. Here you decide when a transfer
        // counts as done. Switch the automatic commit off.

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

    public static void main(String[] args) throws InterruptedException {
        Properties consumerConfig = consumerConfig();
        if (!"false".equals(Objects.toString(consumerConfig.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)))) {
            log.error("The consumer still commits on its own - TODO 1 is still open. See the lab text.");
            System.exit(1);
        }

        Consumer<String, BankTransfer> consumer = new KafkaConsumer<>(consumerConfig);
        Producer<String, Booking> producer = new KafkaProducer<>(producerConfig());
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!running) {
                return; // the app is exiting on its own
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
        try (consumer; producer) {
            consumer.subscribe(List.of(TRANSFERS_TOPIC));
            log.info("Booking '{}' -> '{}' and '{}' (group: {}, halt at transfer: {})",
                    TRANSFERS_TOPIC, DEBITS_TOPIC, CREDITS_TOPIC, GROUP_ID,
                    HALT_AT_TRANSFER == 0 ? "never" : HALT_AT_TRANSFER);

            while (running) {
                ConsumerRecords<String, BankTransfer> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    if (progress.idle()) {
                        checkScaffold(consumer, producer, progress.total());
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

                    // TODO 3: commit this transfer's offset - here, before
                    // the bookings, or at the end, after them? Your group
                    // decides. The committed offset is the NEXT one to read.

                    // TODO 2: the first booking. Debit or credit first -
                    // your group decides. Key every booking by its account.

                    // The process dies here at HALT_AT_TRANSFER. Keep this
                    // line between the two bookings.
                    haltIfDue(HALT_AT_TRANSFER, producer, transfer);

                    // TODO 2: the second booking.

                    // TODO 3: ... or here?

                    progress.countBooked();
                }
            }
        } catch (WakeupException e) {
            // shutdown
        }
        log.info("Stopped after booking {} transfers", progress.total());
    }

    /** Stops the scaffold from looking like it works while TODOs are open. */
    private static void checkScaffold(Consumer<?, ?> consumer, Producer<?, ?> producer, long booked) {
        producer.flush();
        double sent = producer.metrics().entrySet().stream()
                .filter(e -> e.getKey().group().equals("producer-metrics")
                        && e.getKey().name().equals("record-send-total"))
                .mapToDouble(e -> ((Number) e.getValue().metricValue()).doubleValue())
                .sum();
        if (booked > 0 && sent == 0) {
            log.error("{} transfers processed, but not a single booking sent - TODO 2 is still open.", booked);
            running = false;
            System.exit(1);
        }
        Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(consumer.assignment());
        if (committed.values().stream().allMatch(Objects::isNull)) {
            log.error("{} transfers booked, but no offset committed - TODO 3 is still open. "
                    + "Every restart would book everything again.", booked);
            running = false;
            System.exit(1);
        }
    }

    private AtLeastOnceApp() {
    }
}
