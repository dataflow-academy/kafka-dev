package academy.dataflow.wind.consumer;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A finished telemetry producer: 50 turbines, one measurement each per
 * second, keyed by turbine id. Start it instead of your own producer if that
 * one does not run.
 */
public final class TelemetryFeed {

    private static final Logger log = LoggerFactory.getLogger(TelemetryFeed.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "nordwind.scada.public.turbine-telemetry.event";
    private static final long TICK_INTERVAL_MS = 1000;

    private static volatile boolean running = true;
    private static final CountDownLatch stopped = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "telemetry-feed");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            try {
                stopped.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));

        WindParkSimulator simulator = new WindParkSimulator();
        long sent = 0;
        long lastReport = System.currentTimeMillis();
        try (Producer<String, WindTurbineMeasurement> producer = new KafkaProducer<>(props)) {
            log.info("Producing to '{}' every {} ms", TOPIC, TICK_INTERVAL_MS);
            while (running) {
                long tickStart = System.currentTimeMillis();
                for (WindTurbineMeasurement m : simulator.nextTick(Instant.now())) {
                    producer.send(new ProducerRecord<>(TOPIC, m.windTurbineId(), m), (metadata, exception) -> {
                        if (exception != null) {
                            log.error("Send failed: {}", exception.toString());
                        }
                    });
                    sent++;
                }
                if (tickStart - lastReport >= 10_000) {
                    log.info("{} measurements sent", sent);
                    lastReport = tickStart;
                }
                long sleep = TICK_INTERVAL_MS - (System.currentTimeMillis() - tickStart);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
            producer.flush();
        } finally {
            stopped.countDown();
        }
        log.info("Feed stopped after {} measurements", sent);
    }

    private TelemetryFeed() {
    }
}
