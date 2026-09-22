package academy.dataflow.wind.join;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.ThreadMetadata;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyDescription;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab scaffolding — not part of the exercise. It keeps the lab observable and
 * safe to break; you would not write this in a production client.
 */
final class StreamsSupport {

    private static final Logger log = LoggerFactory.getLogger(StreamsSupport.class);

    /** Only for the admin client behind the topic check. */
    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";

    /** The settings every lab app shares. */
    static Properties baseConfig(String applicationId, String stateDir) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.CLIENT_ID_CONFIG, hostname());
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        // Kafka Streams keeps a stopped instance in its group until the
        // session times out (45 s), and a restart waits for that. This
        // internal consumer switch makes a stopped instance leave at once.
        props.put(StreamsConfig.consumerPrefix("internal.leave.group.on.close"), true);
        // The telemetry topic may still hold garbage from the consumer lab.
        // Skip it with a warning instead of stopping.
        props.put(StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class);
        return props;
    }

    /**
     * Stops with "TODO n is still open" while one of the TODO results is
     * still null; the first result belongs to TODO {@code firstTodo}.
     */
    static void exitIfTodosOpen(String what, int firstTodo, Object... results) {
        for (int i = 0; i < results.length; i++) {
            if (results[i] == null) {
                log.error("{} - TODO {} is still open. See the lab text.", what, firstTodo + i);
                System.exit(1);
            }
        }
    }

    /** Stops with the given hint while the topology writes to no topic at all. */
    static void exitIfNotWritingToATopic(Topology topology, String hint) {
        boolean writesToATopic = topology.describe().subtopologies().stream()
                .flatMap(subtopology -> subtopology.nodes().stream())
                .anyMatch(node -> node instanceof TopologyDescription.Sink);
        if (!writesToATopic) {
            log.error("{}. See the lab text.", hint);
            System.exit(1);
        }
    }

    /**
     * Fails early with a readable message if a topic is missing. Without this
     * check, a missing input topic stops the app with a long stack trace, and
     * a missing output topic is silently auto-created with one partition.
     *
     * @return the partition count per topic
     */
    static Map<String, Integer> requireTopics(String... topics) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        try (Admin admin = Admin.create(props)) {
            Set<String> existing = admin.listTopics().names().get();
            List<String> missing = List.of(topics).stream().filter(t -> !existing.contains(t)).toList();
            if (!missing.isEmpty()) {
                log.error("Topic(s) missing: {} - create them first, see the lab text.", missing);
                System.exit(1);
            }
            Map<String, TopicDescription> descriptions = admin.describeTopics(List.of(topics)).allTopicNames().get();
            return descriptions.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue().partitions().size()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            log.error("Cannot reach Kafka at {}: {}", BOOTSTRAP_SERVERS, e.getCause().toString());
            System.exit(1);
            return Map.of();
        }
    }

    /**
     * Starts the app and blocks until Ctrl+C, then closes it cleanly. Exits
     * non-zero if Kafka Streams stopped on its own.
     */
    static void runUntilShutdown(KafkaStreams streams) {
        // An exception nobody caught stops this instance instead of silently
        // replacing the thread - a failure must stay visible.
        streams.setUncaughtExceptionHandler(exception -> {
            log.error("Uncaught exception in a stream thread - shutting down", exception);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });

        CountDownLatch stopped = new CountDownLatch(1);
        streams.setStateListener((newState, oldState) -> {
            log.info("State {} -> {}", oldState, newState);
            if (newState == KafkaStreams.State.RUNNING) {
                logTasks(streams);
            }
            if (newState == KafkaStreams.State.ERROR || newState == KafkaStreams.State.NOT_RUNNING) {
                stopped.countDown();
            }
        });

        CountDownLatch shutdownRequested = new CountDownLatch(1);
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stopped.getCount() == 0) {
                return; // Kafka Streams has already stopped on its own
            }
            log.info("Shutdown signal received, closing Kafka Streams ...");
            shutdownRequested.countDown();
            // close() commits what is in flight.
            streams.close(Duration.ofSeconds(30));
            try {
                main.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));

        streams.start();
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (shutdownRequested.getCount() > 0) {
            log.error("Kafka Streams stopped on its own (state: {}) - see the log above", streams.state());
            System.exit(1);
        }
        log.info("Stopped cleanly");
    }

    /**
     * Writes the topology to topology.txt in the lab folder, so it can be
     * opened and pasted into a visualizer. Also logs it, with the path.
     */
    static void publishTopology(Topology topology) {
        String description = topology.describe().toString();
        log.info("Topology:\n{}", description);
        Path file = labFolder().resolve("topology.txt").toAbsolutePath();
        try {
            Files.writeString(file, description);
            log.info("Topology written to {}", file);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static final AtomicLong measurementsIn = new AtomicLong();
    private static final AtomicLong joined = new AtomicLong();
    private static final AtomicLong withoutMasterData = new AtomicLong();

    /** Counts every measurement that comes in, for the ten-second report. */
    static <K, V> ForeachAction<K, V> countMeasurementIn() {
        return (key, value) -> measurementsIn.incrementAndGet();
    }

    /** Counts every output record, with or without master data, for the ten-second report. */
    static ForeachAction<String, EnrichedMeasurement> countEnriched() {
        return (turbineId, measurement) ->
                (measurement.ratedPowerKw() == null ? withoutMasterData : joined).incrementAndGet();
    }

    /** Logs which topics the app joins, with their partition counts. */
    static void logJoinStart(Map<String, Integer> partitions, String telemetryTopic, String registryTopic,
            String outputTopic) {
        log.info("Enriching '{}' ({} partitions) with '{}' ({} partitions) -> '{}'",
                telemetryTopic, partitions.get(telemetryTopic),
                registryTopic, partitions.get(registryTopic), outputTopic);
    }

    /** Logs every ten seconds how many measurements came in and how many found master data. */
    static void reportEveryTenSeconds() {
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "reporter");
            thread.setDaemon(true);
            return thread;
        });
        reporter.scheduleAtFixedRate(() -> log.info(
                "Last 10 s: {} measurements in, {} with master data, {} without",
                measurementsIn.getAndSet(0), joined.getAndSet(0), withoutMasterData.getAndSet(0)),
                10, 10, TimeUnit.SECONDS);
    }

    /**
     * The Gradle project this class was loaded from. VS Code starts the app in
     * the workspace folder, so the working directory is not the lab folder.
     */
    private static Path labFolder() {
        try {
            Path location =
                    Path.of(StreamsSupport.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path dir = location; dir != null; dir = dir.getParent()) {
                if (Files.isRegularFile(dir.resolve("build.gradle.kts"))) {
                    return dir;
                }
            }
        } catch (URISyntaxException | RuntimeException e) {
            log.debug("No lab folder on the class path, using the working directory: {}", e.toString());
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /** Which tasks - and therefore which partitions - this instance works on. */
    private static void logTasks(KafkaStreams streams) {
        Set<String> active = new TreeSet<>();
        Set<String> standby = new TreeSet<>();
        for (ThreadMetadata thread : streams.metadataForLocalThreads()) {
            thread.activeTasks().forEach(t -> active.add(t.taskId().toString()));
            thread.standbyTasks().forEach(t -> standby.add(t.taskId().toString()));
        }
        log.info("This instance runs {} active task(s): {}{}", active.size(), active,
                standby.isEmpty() ? "" : " and " + standby.size() + " standby task(s): " + standby);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    private StreamsSupport() {
    }
}
