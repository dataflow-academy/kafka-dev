package wind.common;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsUtils {
    public static void runKafkaStreamsApp(Topology topology, Properties streamsProperties) {
        System.out.println("you can paste the topology into this site for a visualization: https://zz85.github.io/kafka-streams-viz/");
        System.out.println(topology.describe());
        final KafkaStreams streams = new KafkaStreams(topology, streamsProperties);

        // Let us run Kafka Streams in the background
        final CountDownLatch latch = new CountDownLatch(1);

        //... when we receive a SIGTERM we close Kafka Streams
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("<<< Stopping the streams-app Application");
            streams.close();
            latch.countDown();
        }));

        try {
            // Start Kafka Streams
            streams.start();
            latch.await();
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
