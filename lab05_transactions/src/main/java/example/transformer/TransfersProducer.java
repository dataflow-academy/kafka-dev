package example.transformer;

import example.common.BankTransfer;
import example.common.BankTransferSerializer;
import example.common.BankTransferSupplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.stream.Stream;

public class TransfersProducer {
    public static void main(final String[] args) {
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BankTransferSerializer.class);
        final String TOPIC = "bank-transactions";

        int msgsPerSec = 1;
        if (args.length == 1) {
            msgsPerSec = Integer.parseInt(args[0]);
        }

        final Stream<BankTransfer> transferStream = Stream.generate(new BankTransferSupplier(msgsPerSec));

        try (Producer<String, BankTransfer> producer = new KafkaProducer<>(props)) {
            transferStream.forEach(bankTransfer -> {
                ProducerRecord<String, BankTransfer> producerRecord = new ProducerRecord<>(TOPIC, bankTransfer);
                producer.send(producerRecord);
                System.out.println("Produced record" + bankTransfer);
            });
        }
    }
}
