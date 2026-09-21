package academy.dataflow.wind.transactions;

import static academy.dataflow.wind.transactions.BookingSupport.BOOTSTRAP_SERVERS;
import static academy.dataflow.wind.transactions.BookingSupport.haltIfDue;
import static academy.dataflow.wind.transactions.BookingSupport.hostname;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Books transfers exactly once: the debit, the credit and the consumer offset
 * go into one Kafka transaction. Either all three become visible or none.
 *
 * <p>The bookings are already there, in the same shape as in AtLeastOnceApp.
 * The TODOs are the transaction around them. Calls into BookingSupport are
 * lab helpers you can skip while reading.
 */
public final class TransactionalApp {

    /** All three bank labs read from this one topic; TransferSource fills it. */
    private static final String TRANSFERS_TOPIC = "nordbank.payments.public.transfer.event";
    private static final String DEBITS_TOPIC = "nordbank.payments.public.debit-transactions.event";
    private static final String CREDITS_TOPIC = "nordbank.payments.public.credit-transactions.event";
    private static final String GROUP_ID = "nordbank-booking-transactional";
    /** The process dies while it books this transfer of the run, between debit and credit. 0 = never. */
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
        // Retry CONCURRENT_TRANSACTIONS sooner than the default 100 ms.
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 10);

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

    public static void main(String[] args) {
        Properties producerConfig = producerConfig();
        Properties consumerConfig = consumerConfig();
        // Lab helper: stops here while TODO 1 or 2 is still open.
        BookingSupport.exitIfTransactionTodosOpen(producerConfig, consumerConfig);

        Consumer<String, BankTransfer> consumer = new KafkaConsumer<>(consumerConfig);
        Producer<String, Booking> producer = new KafkaProducer<>(producerConfig);
        // Ctrl+C or Stop: end the loop.
        BookingSupport.onShutdown(() -> {
            running = false;
            consumer.wakeup();
        });
        // Lab helper: progress lines.
        BookingSupport.Progress progress = new BookingSupport.Progress();

        try (consumer) {
            // TODO 3: once, before the first transaction: register the
            // transactional id with the broker.

            consumer.subscribe(List.of(TRANSFERS_TOPIC));
            BookingSupport.logStart(TRANSFERS_TOPIC, DEBITS_TOPIC, CREDITS_TOPIC, GROUP_ID,
                    producerConfig.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG), HALT_AT_TRANSFER);

            while (running) {
                ConsumerRecords<String, BankTransfer> records = consumer.poll(Duration.ofMillis(500));
                progress.afterPoll(records);
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
            // Lab helper: open TODOs 3 and 4a end up here too.
            BookingSupport.stopAfterClientError(e, producer);
        } finally {
            producer.close();
            BookingSupport.closed(); // Lab helper: releases the shutdown hook.
        }
        progress.logStopped();
    }

    private TransactionalApp() {
    }
}
