package academy.dataflow.wind.join;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes the master data of the fleet: one record per turbine and one per
 * park, to compacted topics. Runs once and exits; running it again is
 * harmless, compaction keeps the latest value per key.
 *
 * <p>This stands in for the asset team's master data service, which is
 * written in Python. It is not part of the exercise - but it behaves like the
 * real thing, see {@link #producerConfig()}.
 */
public final class RegistryPublisher {

    private static final Logger log = LoggerFactory.getLogger(RegistryPublisher.class);

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TURBINE_REGISTRY_TOPIC = "nordwind.assets.public.turbine-registry.state";
    /** Optional: only published if the topic exists. */
    private static final String PARK_REGISTRY_TOPIC = "nordwind.assets.public.park-registry.state";

    private record Park(String id, String name, int turbines, String manufacturer, String model,
                        double ratedPowerKw, String commissioningDate, String operator, String sea) {
    }

    /**
     * The fleet. Turbine counts and rated power match the simulator in the
     * producer lab exactly - the rated power is the denominator of the
     * capacity factor, so both sides have to agree on it.
     */
    private static final List<Park> PARKS = List.of(
            new Park("alpha-ventus", "alpha ventus", 12, "AREVA Wind", "M5000-116", 5000.0, "2010-04-27", "DOTI", "NORTH_SEA"),
            new Park("nordsee-ost", "Nordsee Ost", 15, "Senvion", "6.2M126", 6200.0, "2015-05-01", "RWE", "NORTH_SEA"),
            new Park("borkum-riffgrund", "Borkum Riffgrund 1", 10, "Siemens Gamesa", "SWT-4.0-120", 4000.0, "2015-04-01", "Ørsted", "NORTH_SEA"),
            new Park("arkona", "Arkona", 8, "Siemens Gamesa", "SWT-6.0-154", 6000.0, "2019-04-01", "RWE / Equinor", "BALTIC_SEA"),
            new Park("baltic-eagle", "Baltic Eagle", 5, "Vestas", "V164-7.0", 7000.0, "2024-01-01", "Iberdrola", "BALTIC_SEA"));

    private static Properties producerConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "registry-publisher");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // The asset team's service runs the Python client with its defaults.
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, PythonDefaultPartitioner.class);
        return props;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        boolean withParks = topicExists(PARK_REGISTRY_TOPIC);
        if (!topicExists(TURBINE_REGISTRY_TOPIC)) {
            log.error("Topic '{}' is missing - create it first, see the lab text.", TURBINE_REGISTRY_TOPIC);
            System.exit(1);
        }

        List<ProducerRecord<String, Object>> records = new ArrayList<>();
        for (Park park : PARKS) {
            for (int i = 1; i <= park.turbines(); i++) {
                String turbineId = "%s-%02d".formatted(park.id(), i);
                records.add(new ProducerRecord<>(TURBINE_REGISTRY_TOPIC, turbineId,
                        new WindTurbineRegistration(turbineId, park.id(), park.manufacturer(), park.model(),
                                park.ratedPowerKw(), park.commissioningDate())));
            }
            if (withParks) {
                records.add(new ProducerRecord<>(PARK_REGISTRY_TOPIC, park.id(),
                        new WindParkRegistration(park.id(), park.name(), park.operator(), park.sea())));
            }
        }

        try (Producer<String, Object> producer = new KafkaProducer<>(producerConfig())) {
            List<Future<RecordMetadata>> sent = new ArrayList<>();
            for (ProducerRecord<String, Object> record : records) {
                sent.add(producer.send(record));
            }
            // A one-shot job: wait for every acknowledgement, fail loudly otherwise.
            for (Future<RecordMetadata> future : sent) {
                future.get();
            }
        }
        log.info("Published {} turbines to '{}'",
                PARKS.stream().mapToInt(Park::turbines).sum(), TURBINE_REGISTRY_TOPIC);
        if (withParks) {
            log.info("Published {} parks to '{}'", PARKS.size(), PARK_REGISTRY_TOPIC);
        } else {
            log.info("Topic '{}' does not exist - parks skipped", PARK_REGISTRY_TOPIC);
        }
    }

    private static boolean topicExists(String topic) throws InterruptedException, ExecutionException {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        try (Admin admin = Admin.create(props)) {
            Set<String> names = admin.listTopics().names().get();
            return names.contains(topic);
        }
    }

    private RegistryPublisher() {
    }
}
