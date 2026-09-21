package academy.dataflow.wind.consumer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import academy.dataflow.wind.consumer.ConsumerSupport.LoggingRebalanceListener;
import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads wind turbine telemetry and shows the current power per wind park.
 *
 * <p>Everything outside the TODOs is scaffolding and already works: the
 * overview, the rebalance logging, graceful shutdown. What only keeps the lab
 * observable lives in {@link ConsumerSupport}. Yours are the
 * configuration, the consumer, the subscription, the poll loop and - in the
 * error handling lab - what happens when a record cannot be read.
 */
public final class ConsumerApp {

    /**
     * The consumer configuration.
     *
     * <p>TODO 1: fill in the missing group. The lab text tells you which
     * questions it answers.
     */
    private static Properties consumerConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        // Makes this consumer identifiable in broker logs, metrics and quotas.
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, ConsumerSupport.hostname());
        // The rebalance protocol with broker-side assignment. Use it from
        // Kafka 4.0 on; anything else is legacy.
        props.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "consumer");
        // Never read records of open or aborted transactions. Costs nothing
        // without transactions, so set it always.
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        // Deserialization: the key is a plain string, the value is a
        // WindTurbineMeasurement in JSON. Which deserializers do you need?
        // (The value one comes from io.confluent:kafka-json-serializer and
        // needs no registry. JSON carries no type, so it also needs to be
        // told the target class: KafkaJsonDeserializerConfig.JSON_VALUE_TYPE.)

        // The group: which group does this consumer join (use GROUP_ID), and
        // where does it start when the group has no committed offset yet?

        return props;
    }

    private static final Logger log = LoggerFactory.getLogger(ConsumerApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "nordwind.scada.public.turbine-telemetry.event";
    private static final String GROUP_ID = "turbine-overview";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    private static volatile boolean running = true;

    public static void main(String[] args) {
        ParkOverview overview = new ParkOverview(GROUP_ID);

        // TODO 2: create the consumer and replace both '?' with the right
        // types. They have to match the deserializers from TODO 1.
        Consumer<?, ?> consumer = null; // new KafkaConsumer<>(consumerConfig())

        ConsumerSupport.exitIfMissing(consumer); // Lab helper: stops while TODO 2 is open.

        // Ctrl+C does not kill the JVM on the spot. poll() may be blocking, so
        // the hook calls wakeup(), which makes poll() throw a WakeupException,
        // and waits until main() has closed the consumer.
        ConsumerSupport.onShutdown(() -> {
            running = false;
            consumer.wakeup();
        });

        try {
            // TODO 3: subscribe to TOPIC. Pass new LoggingRebalanceListener(overview)
            // as the second argument, so you see which partitions this
            // instance gets.

            // Lab helper: complains while TODO 3 is open, otherwise logs one start line.
            ConsumerSupport.checkSubscribed(consumer, TOPIC, GROUP_ID, BOOTSTRAP_SERVERS);

            // TODO 4: read in a loop until `running` becomes false:
            //
            // while (running) {
            //     ConsumerRecords<String, WindTurbineMeasurement> records = consumer.poll(POLL_TIMEOUT);
            //     for (ConsumerRecord<String, WindTurbineMeasurement> record : records) {
            //         overview.add(record);
            //     }
            //     overview.maybeLogSummary();
            // }
            //
            // poll() does more than fetch: it sends heartbeats, takes part in
            // rebalances and commits the offsets of the previous poll every
            // 5 seconds. overview.maybeLogSummary() prints the table, at most
            // every ten seconds.
            //
            // TODO 5 - in the error handling lab: put a try/catch for
            // RecordDeserializationException around poll().

            ConsumerSupport.checkPolled(consumer); // Lab helper: complains while TODO 4 is open.
        } catch (WakeupException e) {
            // Thrown by poll() after consumer.wakeup(): the normal way out.
        } finally {
            // close() commits the offsets of what was processed and leaves the
            // group at once, instead of making it wait for a timeout.
            consumer.close();
            log.info("Consumer closed");
            ConsumerSupport.closed(); // Lab helper: releases the shutdown hook.
        }
        ConsumerSupport.exitIfTodoOpen(); // Lab helper: exit code 1 while a TODO is open.
    }

    private ConsumerApp() {
    }
}
