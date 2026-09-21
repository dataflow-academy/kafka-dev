package academy.dataflow.wind.hello;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes one message to Kafka and reads exactly that message back.
 *
 * <p>Nothing to fill in. If this runs, your environment is ready for the
 * labs: Java, the build and the connection to the cluster all work.
 */
public final class HelloApp {

    private static final Logger log = LoggerFactory.getLogger(HelloApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "hello";

    public static void main(String[] args) throws Exception {
        String key = HelloSupport.hostname();
        String value = "Hello from " + key + " at " + Instant.now();

        RecordMetadata written = write(key, value);
        log.info("Wrote     '{}' to partition {}, offset {}", value, written.partition(), written.offset());

        ConsumerRecord<String, String> read = readAt(written.partition(), written.offset());
        log.info("Read back '{}' from partition {}, offset {}", read.value(), read.partition(), read.offset());
    }

    private static RecordMetadata write(String key, String value) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            // get() waits for the broker. Fine for a single message; in a loop
            // it would turn every send into a round trip.
            return producer.send(new ProducerRecord<>(TOPIC, key, value)).get();
        }
    }

    /** Reads the record at one offset of one partition - no consumer group involved. */
    private static ConsumerRecord<String, String> readAt(int partition, long offset) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition topicPartition = new TopicPartition(TOPIC, partition);
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);

            Instant deadline = Instant.now().plusSeconds(10);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.offset() == offset) {
                        return record;
                    }
                }
            }
            throw new IllegalStateException("Offset " + offset + " did not come back within 10 s");
        }
    }

    private HelloApp() {
    }
}
