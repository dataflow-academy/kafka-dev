package academy.dataflow.wind.transactions;

import static academy.dataflow.wind.transactions.BookingSupport.BOOTSTRAP_SERVERS;

import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The data source for the bank labs: writes a fixed number of transfers and
 * stops. Nothing to fill in.
 *
 * <p>A fixed number makes the result countable: after booking, the debit topic
 * and the credit topic must each hold exactly as many records as this run
 * produced. Every run appends another batch with fresh transfer ids, so the
 * topic keeps growing across the three labs.
 */
public final class TransferSource {

    private static final Logger log = LoggerFactory.getLogger(TransferSource.class);

    /** All three bank labs read their transfers from this one topic. */
    private static final String TRANSFERS_TOPIC = "nordbank.payments.public.transfer.event";
    /** How many transfers one run writes. */
    private static final int TRANSFERS = 1000;

    public static void main(String[] args) throws Exception {
        // Lab helper: stops with a hint if the topic has not been created yet.
        BookingSupport.exitIfTopicMissing(TRANSFERS_TOPIC);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "transfer-source");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);

        String run = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        // Lab helper: random transfers between a dozen accounts.
        BookingSupport.TransferGenerator transfers = new BookingSupport.TransferGenerator(run);
        AtomicBoolean failed = new AtomicBoolean(false);

        try (Producer<String, BankTransfer> producer = new KafkaProducer<>(props)) {
            for (int i = 1; i <= TRANSFERS; i++) {
                BankTransfer transfer = transfers.next();
                // Keyed by the paying account: all orders of one customer stay in order.
                producer.send(new ProducerRecord<>(TRANSFERS_TOPIC, transfer.fromAccount(), transfer),
                        (metadata, exception) -> {
                            if (exception != null && failed.compareAndSet(false, true)) {
                                log.error("Could not write a transfer: {}", exception.toString());
                            }
                        });
            }
            producer.flush();
        }
        if (failed.get()) {
            System.exit(1);
        }
        log.info("Wrote {} transfers (ids tx-{}-0001 to tx-{}-{}), {} EUR in total, to {}",
                TRANSFERS, run, run, String.format("%04d", TRANSFERS),
                String.format("%.2f", transfers.totalCents() / 100.0), TRANSFERS_TOPIC);
    }

    private TransferSource() {
    }
}
