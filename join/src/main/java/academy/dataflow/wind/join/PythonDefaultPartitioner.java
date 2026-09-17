package academy.dataflow.wind.join;

import java.util.Map;
import java.util.zip.CRC32;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

/**
 * Places keyed records the way a Python producer does out of the box.
 *
 * <p>The Python client (confluent-kafka, built on librdkafka) defaults to the
 * {@code consistent_random} partitioner: CRC32 of the key bytes, modulo the
 * partition count. The Java client uses murmur2 instead.
 */
public final class PythonDefaultPartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                         Object value, byte[] valueBytes, Cluster cluster) {
        int partitions = cluster.partitionCountForTopic(topic);
        CRC32 crc = new CRC32();
        crc.update(keyBytes);
        return (int) (crc.getValue() % partitions);
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public void close() {
    }
}
