package wind;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class WindTurbineAvroConsumer {
    public static void main(final String[] args) throws IOException {
        final Properties props = new Properties();
        // What do you need to configure here?

        // Configure to use specific Avro classes instead of generic records
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        final String TOPIC = "wind-turbine-data-avro";

        final Consumer consumer = null; // todo

        try (consumer) {
            // Todo: How to define from which topics to consume?

            System.out.println("Started…");
            // In Kafka Consumers we use a infinite loop and fetch the new records in the
            // loop
            while (true) {
                // hint: ConsumerRecords consists of zero or many records
                ConsumerRecords records = null; // todo (hint: do not forget the generic types signature)
                // Please print all records (for your convenience) and add id to the
                // `measurements`-HashMap using the following code:
                // measurements.put(key, value);
            }
        }
    }
}
