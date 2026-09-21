package academy.dataflow.wind.join;

import java.util.Map;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.AutoOffsetReset;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enriches the telemetry with turbine master data and computes the capacity
 * factor of every measurement.
 *
 * <p>Everything outside the TODOs is scaffolding and already works. Three
 * things are yours: the table, the join, and the capacity factor.
 */
public final class JoinApp {

    private static final Logger log = LoggerFactory.getLogger(JoinApp.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TELEMETRY_TOPIC = "nordwind.scada.public.turbine-telemetry.event";
    private static final String REGISTRY_TOPIC = "nordwind.assets.public.turbine-registry.state";
    private static final String OUTPUT_TOPIC = "nordwind.scada.public.turbine-telemetry-enriched.event";
    /** Consumer group, prefix of the internal topics, name of the state directory. */
    private static final String APPLICATION_ID = "turbine-telemetry-enricher";
    /** Where the local state stores live, one subdirectory per application.id. */
    private static final String STATE_DIR = System.getProperty("user.home") + "/kafka-streams";

    private static Properties streamsConfig() {
        return StreamsSupport.baseConfig(BOOTSTRAP_SERVERS, APPLICATION_ID, STATE_DIR);
    }

    private static Topology buildTopology() {
        Serde<String> stringSerde = Serdes.String();
        Serde<WindTurbineMeasurement> measurementSerde = JsonSerde.of(WindTurbineMeasurement.class);
        Serde<WindTurbineRegistration> registrationSerde = JsonSerde.of(WindTurbineRegistration.class);
        Serde<EnrichedMeasurement> enrichedSerde = JsonSerde.of(EnrichedMeasurement.class);

        StreamsBuilder builder = new StreamsBuilder();

        // Only new telemetry. The backlog from earlier labs is older than the
        // master data, and Kafka Streams processes older records first.
        KStream<String, WindTurbineMeasurement> telemetry = builder
                .stream(TELEMETRY_TOPIC, Consumed.with(stringSerde, measurementSerde)
                        .withOffsetResetPolicy(AutoOffsetReset.latest()))
                // Lab helper: counts for the ten-second report.
                .peek(StreamsSupport.countMeasurementIn());

        // TODO 1: read REGISTRY_TOPIC as a table.
        KTable<String, WindTurbineRegistration> registry = null;

        // TODO 2: join the telemetry with the registry. The joiner is a lambda
        // (measurement, registration) -> ...; EnrichedMeasurement.of() copies
        // both sides into the output record. join() or leftJoin() - that is
        // your decision from the lab text.
        KStream<String, EnrichedMeasurement> enriched = null;

        // TODO 3: fill in the capacity factor with mapValues(). Power divided
        // by rated power, rounded to three decimals; withCapacityFactor()
        // returns the record with it. Without master data there is nothing to
        // divide by.
        KStream<String, EnrichedMeasurement> withCapacityFactor = null;

        // Lab helper: stops with a hint while a TODO is still open.
        StreamsSupport.requireTodos("No join yet", 1, registry, enriched, withCapacityFactor);

        withCapacityFactor
                // Lab helper: counts for the ten-second report.
                .peek(StreamsSupport.countEnriched())
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, enrichedSerde));

        return builder.build();
    }

    public static void main(String[] args) {
        Topology topology = buildTopology();
        // Lab helper: writes topology.txt and checks the topics.
        StreamsSupport.publishTopology(topology);

        Map<String, Integer> partitions =
                StreamsSupport.requireTopics(BOOTSTRAP_SERVERS, TELEMETRY_TOPIC, REGISTRY_TOPIC, OUTPUT_TOPIC);
        log.info("Enriching '{}' ({} partitions) with '{}' ({} partitions) -> '{}'",
                TELEMETRY_TOPIC, partitions.get(TELEMETRY_TOPIC),
                REGISTRY_TOPIC, partitions.get(REGISTRY_TOPIC), OUTPUT_TOPIC);

        // Lab helper: logs the counts every ten seconds.
        StreamsSupport.reportEveryTenSeconds();
        // Lab helper: starts Kafka Streams and closes it on Ctrl+C.
        StreamsSupport.runUntilShutdown(new KafkaStreams(topology, streamsConfig()));
    }

    private JoinApp() {
    }
}
