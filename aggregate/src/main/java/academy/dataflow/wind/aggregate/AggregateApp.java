package academy.dataflow.wind.aggregate;

import java.util.Properties;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The running average power per wind turbine, over everything the turbine
 * has ever reported - no windows.
 *
 * <p>Everything outside the TODOs is scaffolding and already works. Three
 * things are yours: the state update, the aggregation, and the output.
 */
public final class AggregateApp {

    private static final Logger log = LoggerFactory.getLogger(AggregateApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String INPUT_TOPIC = "nordwind.scada.public.turbine-telemetry.event";
    private static final String OUTPUT_TOPIC = "nordwind.scada.public.turbine-power-average.state";
    /** Consumer group, prefix of the internal topics, name of the state directory. */
    private static final String APPLICATION_ID = "turbine-power-average";
    /**
     * How often Kafka Streams commits. Each commit also flushes the record
     * cache, which is when updated averages reach the output topic.
     */
    private static final long COMMIT_INTERVAL_MS = 5000;
    /** Where the local state stores live, one subdirectory per application.id. */
    private static final String STATE_DIR = System.getProperty("user.home") + "/kafka-streams";

    private static Properties streamsConfig() {
        Properties props = StreamsSupport.baseConfig(BOOTSTRAP_SERVERS, APPLICATION_ID, STATE_DIR);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, COMMIT_INTERVAL_MS);
        return props;
    }

    private static Topology buildTopology() {
        Serde<String> stringSerde = Serdes.String();
        Serde<WindTurbineMeasurement> measurementSerde = JsonSerde.of(WindTurbineMeasurement.class);
        Serde<TurbinePowerAverage> averageSerde = JsonSerde.of(TurbinePowerAverage.class);

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, WindTurbineMeasurement> telemetry =
                builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, measurementSerde));

        // TODO 2: group the telemetry by turbine and aggregate it.
        //
        // aggregate() takes three arguments:
        //   - an initializer:  () -> the state before the first measurement
        //   - an aggregator:   (key, measurement, state) -> the new state
        //   - Materialized.with(key serde, state serde): how the state store
        //     turns the state into bytes
        // TurbinePowerAverage already has what the first two need.
        KTable<String, TurbinePowerAverage> averages = null;

        // TODO 3: write every change of the table to OUTPUT_TOPIC. A KTable
        // has no to() - turn it into a stream first. The output needs serdes
        // for key and value, just like the input.

        // Lab helper: stops with a hint while TODO 2 is still open.
        StreamsSupport.requireTodos("No aggregation yet", 2, averages);
        return builder.build();
    }

    public static void main(String[] args) {
        // Lab helper: tries add() with the numbers from the slide (TODO 1).
        StreamsSupport.requireAverageWorks();

        Topology topology = buildTopology();
        // Lab helper: stops while nothing writes to a topic (TODO 3).
        StreamsSupport.requireSink(topology, "The topology writes nowhere - TODO 3 is still open");
        // Lab helper: writes topology.txt and checks the topics.
        StreamsSupport.publishTopology(topology);
        StreamsSupport.requireTopics(BOOTSTRAP_SERVERS, INPUT_TOPIC, OUTPUT_TOPIC);
        log.info("Averaging '{}' -> '{}' (application.id: {})", INPUT_TOPIC, OUTPUT_TOPIC, APPLICATION_ID);
        // Lab helper: starts Kafka Streams and closes it on Ctrl+C.
        StreamsSupport.runUntilShutdown(new KafkaStreams(topology, streamsConfig()));
    }

    private AggregateApp() {
    }
}
