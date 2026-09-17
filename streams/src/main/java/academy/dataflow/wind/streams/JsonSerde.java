package academy.dataflow.wind.streams;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.util.Map;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

/**
 * A Serde for JSON records, built from Confluent's schemaless JSON serializer
 * and deserializer. Confluent ships the two only separately.
 */
final class JsonSerde {

    static <T> Serde<T> of(Class<T> type) {
        KafkaJsonSerializer<T> serializer = new KafkaJsonSerializer<>();
        serializer.configure(Map.of(), false);

        KafkaJsonDeserializer<T> deserializer = new KafkaJsonDeserializer<>();
        deserializer.configure(Map.of(KafkaJsonDeserializerConfig.JSON_VALUE_TYPE, type.getName()), false);

        return Serdes.serdeFrom(serializer, deserializer);
    }

    private JsonSerde() {
    }
}
