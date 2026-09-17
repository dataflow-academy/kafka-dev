package academy.dataflow.wind.transactions;

import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.Random;
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
 * and the credit topic must each hold exactly as many records as there are
 * transfers. Every run adds another batch with fresh transfer ids.
 */
public final class TransferSource {

    private static final Logger log = LoggerFactory.getLogger(TransferSource.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TRANSFERS_TOPIC = "nordbank.payments.public.transfer.event";
    /** How many transfers one run writes. */
    private static final int TRANSFERS = 1000;

    private static final List<String> ACCOUNTS = List.of(
            "alice", "bob", "carol", "dave", "erin", "frank",
            "grace", "heidi", "ivan", "judy", "mallory", "oscar");

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "transfer-source");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);

        String run = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Random random = new Random();
        long totalCents = 0;
        AtomicBoolean failed = new AtomicBoolean(false);

        try (Producer<String, BankTransfer> producer = new KafkaProducer<>(props)) {
            for (int i = 1; i <= TRANSFERS; i++) {
                String from = ACCOUNTS.get(random.nextInt(ACCOUNTS.size()));
                String to = from;
                while (to.equals(from)) {
                    to = ACCOUNTS.get(random.nextInt(ACCOUNTS.size()));
                }
                // Mostly small amounts, now and then a large one.
                long amountCents = random.nextInt(10) == 0
                        ? 500_000 + random.nextInt(1_000_000)
                        : 100 + random.nextInt(20_000);
                BankTransfer transfer = new BankTransfer(
                        String.format("tx-%s-%04d", run, i), from, to, amountCents, System.currentTimeMillis());

                // Keyed by the paying account: all orders of one customer stay in order.
                producer.send(new ProducerRecord<>(TRANSFERS_TOPIC, from, transfer), (metadata, exception) -> {
                    if (exception != null && failed.compareAndSet(false, true)) {
                        log.error("Could not write a transfer: {}", exception.toString());
                    }
                });
                totalCents += amountCents;
            }
            producer.flush();
        }
        if (failed.get()) {
            System.exit(1);
        }
        log.info("Wrote {} transfers (ids tx-{}-0001 to tx-{}-{}), {} EUR in total, to '{}'",
                TRANSFERS, run, run, String.format("%04d", TRANSFERS),
                String.format("%.2f", totalCents / 100.0), TRANSFERS_TOPIC);
    }

    private TransferSource() {
    }
}
