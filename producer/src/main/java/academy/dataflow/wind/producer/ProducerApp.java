package academy.dataflow.wind.producer;

import static academy.dataflow.wind.producer.ProducerSupport.giveUp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces wind turbine telemetry to Kafka.
 *
 * <p>Everything outside the TODOs is scaffolding and already works: the
 * simulator, the tick loop, logging. What only keeps the lab observable lives
 * in {@link ProducerSupport}. Yours are the configuration, the producer, the
 * record you send, what happens when a send fails, and - in the reliability
 * lab - a clean shutdown.
 */
public final class ProducerApp {

    /**
     * The producer configuration.
     *
     * <p>TODO 1: fill this in, one group per lab. The lab text tells you
     * which questions each group of settings answers.
     */
    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092,localhost:9093,localhost:9094");
        // Makes this producer identifiable in broker logs, metrics and quotas.
        props.put(ProducerConfig.CLIENT_ID_CONFIG, ProducerSupport.hostname());

        // Serialization: the key is a plain string, the value is a
        // WindTurbineMeasurement. Which serializers do you need?
        // (The value one comes from io.confluent:kafka-json-serializer and
        // needs no registry - it is a thin Jackson wrapper.)

        // Reliability - we get to this in the reliability lab: what has to be
        // true before the broker confirms a write, and what stops a retry from
        // creating a duplicate?

        // Throughput - we get to this in the performance lab: how long may the
        // producer collect before sending, how much may it collect, and should
        // it compress?

        return props;
    }

    private static final Logger log = LoggerFactory.getLogger(ProducerApp.class);

    private static final String TOPIC = "nordwind.scada.public.turbine-telemetry.event";
    /**
     * How often every turbine reports, in milliseconds. 0 removes the brake
     * and turns a run into a measurement that stops after two minutes.
     */
    private static final long TICK_INTERVAL_MS = 1000;

    public static void main(String[] args) throws InterruptedException {
        WindParkSimulator simulator = new WindParkSimulator();
        long produced = 0;
        ProducerSupport.startStats(simulator); // Lab helper: numbers for the progress lines.

        // TODO 2: create the producer and replace both '?' with the right
        // types. Look at what you are sending and at the serializers you
        // configured in TODO 1 - they have to match.
        Producer<?, ?> producer = null; // new KafkaProducer<>(producerConfig())

        ProducerSupport.exitIfMissing(producer); // Lab helper: stops while TODO 2 is open.

        // TODO 5 - in the reliability lab: a clean shutdown. Make Ctrl+C end
        // the loop below. ProducerSupport.onShutdown(...) runs your code when
        // the JVM is asked to stop, and waits until the producer is closed.

        try {
            ProducerSupport.logStart(TOPIC, TICK_INTERVAL_MS); // Lab helper: one start line.

            while (!ProducerSupport.gaveUp()) {
                long tickStart = System.currentTimeMillis();
                if (ProducerSupport.measurementOver(TICK_INTERVAL_MS)) {
                    break;
                }

                List<WindTurbineMeasurement> measurements = simulator.nextTick(Instant.now());
                for (WindTurbineMeasurement measurement : measurements) {
                    // TODO 3: build the record and send it.
                    //
                    // a) Which value belongs in the key? Your choice decides
                    //    which measurements land on the same partition - and
                    //    therefore what stays in order. Whatever you pick,
                    //    be able to say why.
                    //
                    // b) send() is asynchronous: it appends to a local batch
                    //    and returns. Do NOT call .get() on the returned
                    //    future per message - that turns every send into a
                    //    network round trip and destroys batching.
                    //
                    // ProducerRecord<?, ?> record = new ProducerRecord<>(TOPIC, ??, measurement);
                    // producer.send(record);
                    //
                    // TODO 4 - in the reliability lab: pass a callback as the
                    // second argument to send(). It runs when the broker
                    // acknowledged, or when delivery failed for good - by then
                    // the client has already exhausted its internal retries.
                    // What now? The lab text has three options and one
                    // anti-pattern; for "stop", call giveUp(exception).
                    //
                    // producer.send(record, (metadata, exception) -> {
                    //     if (exception != null) {
                    //         ...
                    //     }
                    // });

                    produced++;
                }

                ProducerSupport.maybeLogProgress(producer); // Lab helper: throughput every 5 s.

                // Keep a steady tick rate regardless of how long sending took.
                long sleep = TICK_INTERVAL_MS - (System.currentTimeMillis() - tickStart);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }

            if (!ProducerSupport.gaveUp()) {
                // Flush, so the summary covers every record, not just the
                // ones acknowledged so far.
                producer.flush();
                ProducerSupport.logSummary(producer); // Lab helper: totals of the run.
            }
        } finally {
            // A producer that is not closed loses whatever sits in its batches.
            // After giveUp() there is nothing worth waiting for: close at once
            // and let the remaining sends fail. Otherwise give them 10 s.
            producer.close(ProducerSupport.gaveUp() ? Duration.ZERO : Duration.ofSeconds(10));
        }
        ProducerSupport.exit(produced); // Lab helper: exit code 1 after giveUp().
    }

    private ProducerApp() {
    }
}
