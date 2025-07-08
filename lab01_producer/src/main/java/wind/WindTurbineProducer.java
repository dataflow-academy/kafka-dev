package wind;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.stream.Stream;

public class WindTurbineProducer {
    public static void main(final String[] args) {
        final Properties props = new Properties();
        // TODO: Which settings are relevant here?
        // props.put(??, ??);
        // Please note, we don't want to produce strings now, but WindTurbineData.
        // Fortunately, we already have a serialization class ready to go

        // Hint: Before you continue please check the WindTurbineDataSerializer

        // Hints:
        // How to connect to Kafka?
        // How to serialize Keys?
        // How to serialize Values?

        final String TOPIC = "wind-turbine-data";

        // The WindTurbineDataSupplier creates a Stream of approximately `msgsPerSec`
        // messages per seconds for you to produce
        int msgsPerSec = 1;
        if (args.length == 1) {
            msgsPerSec = Integer.parseInt(args[0]);
        }
        final Stream<WindTurbineData> windTurbineDataStream = Stream
                .generate(new WindTurbineDataSupplier(50, msgsPerSec));

        // Todo initialize a producer
        // Producer<??, ??> producer = new ??
        Producer producer = null;

        // Please always close the producers. try(var) {} closes it automatically
        try (producer) {
            // Read from the stream
            windTurbineDataStream.forEach(turbineData -> {
                // Todo
                // The Key of the message to send should be the ID of the wind turbine
                // Please produce the data to the Topic `TOPIC`
                // The value is the turbineData

                // Hint: ProducerRecord?? ?? = ??

                // Hint: Check the message signature for ideas what you need to provide here.
                producer.send(null);
                System.out.println("Produced data for wind turbine " + turbineData.windTurbineId);
            });
        }
    }
}
