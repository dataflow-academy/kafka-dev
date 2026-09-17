package academy.dataflow.wind.aggregate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.ThreadMetadata;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scaffolding shared by the lab apps: topic check, lifecycle, logging.
 * Nothing in here is part of the exercise.
 */
final class StreamsSupport {

    private static final Logger log = LoggerFactory.getLogger(StreamsSupport.class);

    /** The settings every lab app shares. */
    static Properties baseConfig(String bootstrapServers, String applicationId, String stateDir) {
        Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.CLIENT_ID_CONFIG, hostname());
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
     * Fails early with a readable message if a topic is missing. Without this
     * check, a missing input topic stops the app with a long stack trace, and
     * a missing output topic is silently auto-created with one partition.
     *
     * @return the partition count per topic
     */
    static Map<String, Integer> requireTopics(String bootstrapServers, String... topics) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
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
            log.error("Cannot reach Kafka at {}: {}", bootstrapServers, e.getCause().toString());
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, closing Kafka Streams ...");
            shutdownRequested.countDown();
            // close() commits what is in flight.
            streams.close(Duration.ofSeconds(30));
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
