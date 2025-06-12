package wind;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;

import java.util.Properties;
import java.util.stream.Stream;

public class WindTurbineAvroProducer {
    public static void main(final String[] args) {
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        // TODO: Configure the producer

        final String TOPIC = "wind-turbine-data-avro";

        // The WindTurbineDataSupplier creates a Stream of approximately `msgsPerSec`
        // messages per seconds for you to produce
        int msgsPerSec = 1;
        if (args.length == 1) {
            msgsPerSec = Integer.parseInt(args[0]);
        }
        final Stream<WindTurbineData> windTurbineDataStream = Stream
                .generate(new WindTurbineDataSupplier(50, msgsPerSec));

        Producer<String, WindTurbineData> producer = new KafkaProducer<>(props);

        try (producer) {
            windTurbineDataStream.forEach(turbineData -> {
                // TODO: Produce the data
                System.out.println("Produced data for wind turbine " + turbineData.getWindTurbineId());
            });
        }
    }
    //
}
