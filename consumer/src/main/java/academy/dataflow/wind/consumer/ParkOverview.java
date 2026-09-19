package academy.dataflow.wind.consumer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The processing: the latest power of every turbine, summed up per park and
 * logged every 10 seconds.
 *
 * <p>It also remembers, per partition, the last offset it has processed - in
 * a small file, as a real sink would. When a partition starts again at an
 * offset that was already processed, it says how many records it processes a
 * second time.
 */
final class ParkOverview {

    private static final Logger log = LoggerFactory.getLogger(ParkOverview.class);
    private static final long PRINT_INTERVAL_MS = 10_000;

    private record Latest(int partition, WindTurbineMeasurement measurement) {
    }

    /** Turbine id -> its latest measurement. Sorted by park, then turbine. */
    private final Map<String, Latest> turbines = new TreeMap<>();
    private final Path offsetDir;
    /** Partition -> last processed offset, as it is on disk. */
    private final Map<Integer, Long> processed = new HashMap<>();
    private final Set<Integer> dirty = new HashSet<>();
    /** Partitions whose first record since the assignment is still to come. */
    private final Set<Integer> awaitingFirst = new HashSet<>();
    private final Set<Integer> assigned = new TreeSet<>();
    /** What the first records after an assignment revealed, logged per poll. */
    private final Map<Integer, String> replayed = new TreeMap<>();
    private final Set<Integer> continued = new TreeSet<>();
    private long replayedRecords;

    private long records;
    private long lastPrint = System.currentTimeMillis();

    ParkOverview(String groupId) {
        offsetDir = Path.of(System.getProperty("java.io.tmpdir"), "turbine-overview", groupId);
        try {
            Files.createDirectories(offsetDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Processes one record. */
    void add(ConsumerRecord<String, WindTurbineMeasurement> record) {
        WindTurbineMeasurement m = record.value();
        if (m == null || m.windParkId() == null || m.windTurbineId() == null) {
            throw new IllegalArgumentException("Not a measurement at partition " + record.partition()
                    + " offset " + record.offset() + ": " + m);
        }
        turbines.put(m.windParkId() + "/" + m.windTurbineId(), new Latest(record.partition(), m));

        int partition = record.partition();
        if (awaitingFirst.remove(partition)) {
            checkForReplay(partition, record.offset());
        }
        processed.put(partition, record.offset());
        dirty.add(partition);
        records++;
    }

    /** Call once per poll, after all records of that poll were added. */
    void pollDone() {
        for (int partition : dirty) {
            writeOffset(partition, processed.get(partition));
        }
        dirty.clear();

        long now = System.currentTimeMillis();
        boolean printNow = now - lastPrint >= PRINT_INTERVAL_MS;
        // One line per assignment, once every partition has shown its first record.
        if (awaitingFirst.isEmpty() || printNow) {
            logReplays();
        }
        if (printNow) {
            print(now - lastPrint);
            lastPrint = now;
            records = 0;
        }
    }

    void assigned(Collection<TopicPartition> partitions) {
        for (TopicPartition tp : partitions) {
            assigned.add(tp.partition());
            awaitingFirst.add(tp.partition());
            processed.put(tp.partition(), readOffset(tp.partition()));
        }
    }

    /** Forgets the turbines of partitions this instance no longer owns. */
    void revoked(Collection<TopicPartition> partitions) {
        for (TopicPartition tp : partitions) {
            assigned.remove(tp.partition());
            awaitingFirst.remove(tp.partition());
            turbines.values().removeIf(latest -> latest.partition() == tp.partition());
        }
    }

    private void checkForReplay(int partition, long offset) {
        Long last = processed.get(partition);
        if (last == null || last < 0) {
            return;
        }
        if (offset <= last) {
            replayed.put(partition, offset + "-" + last);
            replayedRecords += last - offset + 1;
        } else if (offset == last + 1) {
            continued.add(partition);
        } else {
            log.info("Partition {} continues at offset {}, {} record(s) after offset {} are not processed",
                    partition, offset, offset - last - 1, last);
        }
    }

    private void logReplays() {
        if (!replayed.isEmpty()) {
            log.warn("{} records are processed a second time (partition=offsets {})",
                    replayedRecords, replayed);
        }
        if (!continued.isEmpty()) {
            log.info("Partitions {} continue after the last processed offset, nothing is processed twice",
                    continued);
        }
        replayed.clear();
        continued.clear();
        replayedRecords = 0;
    }

    private void print(long elapsedMs) {
        if (assigned.isEmpty()) {
            log.info("No partitions assigned to this instance (yet)");
            return;
        }
        if (records == 0) {
            log.warn("Nothing processed in the last {} s. Is a producer running? Is TODO 4 done?",
                    elapsedMs / 1000);
            return;
        }
        Map<String, double[]> parks = new TreeMap<>();
        for (Latest latest : turbines.values()) {
            double[] sums = parks.computeIfAbsent(latest.measurement().windParkId(), p -> new double[2]);
            sums[0]++;
            sums[1] += latest.measurement().powerKw();
        }
        StringBuilder table = new StringBuilder();
        table.append(String.format("%d records in %d s, partitions %s%n",
                records, elapsedMs / 1000, assigned));
        table.append(String.format("    %-18s %8s %10s%n", "park", "turbines", "power MW"));
        double turbinesTotal = 0;
        double powerTotal = 0;
        for (var park : parks.entrySet()) {
            table.append(String.format("    %-18s %8.0f %10.1f%n",
                    park.getKey(), park.getValue()[0], park.getValue()[1] / 1000));
            turbinesTotal += park.getValue()[0];
            powerTotal += park.getValue()[1];
        }
        table.append(String.format("    %-18s %8.0f %10.1f", "total", turbinesTotal, powerTotal / 1000));
        log.info(table.toString());
    }

    private long readOffset(int partition) {
        Path file = offsetDir.resolve("partition-" + partition);
        try {
            return Files.exists(file) ? Long.parseLong(Files.readString(file).trim()) : -1;
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    private void writeOffset(int partition, long offset) {
        Path file = offsetDir.resolve("partition-" + partition);
        Path tmp = offsetDir.resolve("partition-" + partition + ".tmp");
        try {
            Files.writeString(tmp, Long.toString(offset));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Integer> numbers(Collection<TopicPartition> partitions) {
        return partitions.stream().map(TopicPartition::partition).sorted().toList();
    }
}
