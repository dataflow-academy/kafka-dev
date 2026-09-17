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
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Books transfers exactly once: the debit, the credit and the consumer offset
 * go into one Kafka transaction. Either all three become visible or none.
 *
 * <p>The bookings are already there, in the same shape as in AtLeastOnceApp.
 * The TODOs are the transaction around them.
 */
public final class TransactionalApp {

    private static final Logger log = LoggerFactory.getLogger(TransactionalApp.class);

    private static final String GROUP_ID = "nordbank-booking-transactional";
    /**
     * The process dies while it books this transfer (counted from the start
     * of this run), between the debit and the credit. 0 = never.
     */
    private static final long HALT_AT_TRANSFER = 0;

    /** TODO 1: make the producer transactional. */
    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, hostname());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);

        // TODO 1: the transactional id. Which value? It decides what happens
        // to a half-finished transaction when the app restarts.

        return props;
    }

    /** TODO 2: who commits the offsets now, and what may the consumer see? */
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

        // TODO 2: two settings. The offsets travel inside the transaction,
        // so the consumer must not commit on its own. And this consumer must
        // only see transfers from committed transactions.

        return props;
    }

    private static volatile boolean running = true;
    /** Counts down once the loop has ended, however it ended. */
    private static final CountDownLatch stopped = new CountDownLatch(1);

    public static void main(String[] args) {
        Properties producerConfig = producerConfig();
        Properties consumerConfig = consumerConfig();
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

        Consumer<String, BankTransfer> consumer = new KafkaConsumer<>(consumerConfig);
        Producer<String, Booking> producer = new KafkaProducer<>(producerConfig);
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
        boolean failed = false;
        try (consumer) {
            // TODO 3: once, before the first transaction: register the
            // transactional id with the broker.

            consumer.subscribe(List.of(TRANSFERS_TOPIC));
            log.info("Booking '{}' -> '{}' and '{}' (group: {}, transactional id: {}, halt at transfer: {})",
                    TRANSFERS_TOPIC, DEBITS_TOPIC, CREDITS_TOPIC, GROUP_ID,
                    producerConfig.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG),
                    HALT_AT_TRANSFER == 0 ? "never" : HALT_AT_TRANSFER);

            while (running) {
                ConsumerRecords<String, BankTransfer> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    progress.idle();
                    continue;
                }
                for (ConsumerRecord<String, BankTransfer> record : records) {
                    if (!running) {
                        break; // shutdown: the rest of this poll is read again next time
                    }
                    BankTransfer transfer = record.value();
                    Booking debit = Booking.debit(transfer);
                    Booking credit = Booking.credit(transfer);

                    // TODO 4a: one transaction per transfer. Begin it.

                    producer.send(new ProducerRecord<>(DEBITS_TOPIC, debit.account(), debit));
                    haltIfDue(HALT_AT_TRANSFER, producer, transfer);
                    producer.send(new ProducerRecord<>(CREDITS_TOPIC, credit.account(), credit));

                    // TODO 4b: hand this transfer's offset to the transaction
                    // (the NEXT offset to read, together with the consumer's
                    // group metadata) and commit the transaction.

                    progress.countBooked();
                }
            }
        } catch (WakeupException e) {
            // shutdown
        } catch (KafkaException | IllegalStateException e) {
            // Open TODOs 3 and 4a end up here too: the producer refuses to
            // send without initTransactions() and beginTransaction().
            log.error("Stopped by the Kafka client: {}", e.toString());
            failed = true;
        } finally {
            if (failed) {
                // The producer may hold records it can never send (e.g. one
                // outside a transaction); close() would wait for them forever.
                producer.close(Duration.ZERO);
            } else {
                producer.close();
            }
            stopped.countDown();
        }
        if (failed) {
            running = false;
            System.exit(1);
        }
        log.info("Stopped after booking {} transfers", progress.total());
    }

    private TransactionalApp() {
    }
}
