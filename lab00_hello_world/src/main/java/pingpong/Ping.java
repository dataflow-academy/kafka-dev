package pingpong;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class Ping {
    public static void main(String[] args) throws InterruptedException {
        int numMessages = 50;
        if (args.length == 1) {
            numMessages = Integer.parseInt(args[0]);
        }

        // Configuring the Kafka Producer
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Please always close the producers. try(var) {} closes it automatically
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < numMessages; i++) {
                String msg = "Ping " + i;
                Thread.sleep(1000);
                // Create a message (record) to send to Kafka
                ProducerRecord<String, String> producerRecord = new ProducerRecord<>("ping", msg);
                // Send it
                producer.send(producerRecord);
                System.out.println("Produced message " + msg);
            }
            System.out.println("Produced " + numMessages + " Messages");
        }
    }
}
