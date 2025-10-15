package pingpong;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class Pong {
    public static void main(String[] args) {

        // Properties for Consumers
        final Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "some-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Do not do this in your consumers!
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Please always close the consumers. try(var) {} closes it automatically
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            // Subscribe to a list of topics (just one here)
            consumer.subscribe(List.of("ping"));
            // this is a infinite loop that polls new records
            while (true) {
                // It returns 0 or more records
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    String value = record.value();
                    System.out.println("Received Ping: " + value);
                }
            }
        }
    }
}
