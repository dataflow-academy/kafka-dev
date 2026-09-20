package academy.dataflow.wind.join;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    /** Counted while the records flow through, logged every ten seconds. */
    private static final AtomicLong measurementsIn = new AtomicLong();
    private static final AtomicLong joined = new AtomicLong();
    private static final AtomicLong withoutMasterData = new AtomicLong();

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
                .peek((turbineId, measurement) -> measurementsIn.incrementAndGet());

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

        if (registry == null || enriched == null || withCapacityFactor == null) {
            int todo = registry == null ? 1 : enriched == null ? 2 : 3;
            log.error("No join yet - TODO {} is still open. See the lab text.", todo);
            System.exit(1);
        }

        withCapacityFactor
                .peek((turbineId, measurement) ->
                        (measurement.ratedPowerKw() == null ? withoutMasterData : joined).incrementAndGet())
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, enrichedSerde));

        return builder.build();
    }

    public static void main(String[] args) {
        Topology topology = buildTopology();
        StreamsSupport.publishTopology(topology);

        Map<String, Integer> partitions =
                StreamsSupport.requireTopics(BOOTSTRAP_SERVERS, TELEMETRY_TOPIC, REGISTRY_TOPIC, OUTPUT_TOPIC);
        log.info("Enriching '{}' ({} partitions) with '{}' ({} partitions) -> '{}'",
                TELEMETRY_TOPIC, partitions.get(TELEMETRY_TOPIC),
                REGISTRY_TOPIC, partitions.get(REGISTRY_TOPIC), OUTPUT_TOPIC);

        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "reporter");
            thread.setDaemon(true);
            return thread;
        });
        reporter.scheduleAtFixedRate(() -> log.info(
                "Last 10 s: {} measurements in, {} with master data, {} without",
                measurementsIn.getAndSet(0), joined.getAndSet(0), withoutMasterData.getAndSet(0)),
                10, 10, TimeUnit.SECONDS);

        StreamsSupport.runUntilShutdown(new KafkaStreams(topology, streamsConfig()));
    }

    private JoinApp() {
    }
}
