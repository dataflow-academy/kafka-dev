package wind;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import wind.common.JSONSerde;
import wind.common.KafkaStreamsUtils;
import wind.common.WindSerdes;
import wind.common.WindTurbineData;

import java.io.IOException;
import java.util.Properties;

public class AverageWindTurbinePower {

    public static void main(final String[] args) throws IOException {
        final Properties settings = new Properties();
        // todo: Provide the correct properties

        // Instead of  writing the same code over and over again, lets put it in a Helper-Class
        KafkaStreamsUtils.runKafkaStreamsApp(getTopology(), settings);
    }

    private static Topology getTopology() {
        StreamsBuilder builder = new StreamsBuilder();

        // As before: Get data from the `wind-turbine-data` topic
        final KStream<String, WindTurbineData> windTurbineData = builder.stream("wind-turbine-data",
                Consumed.with(Serdes.String(), WindSerdes.WindTurbineDataSerde()));

        // Task 1: Count the number of messages per Key in the windTurbineData stream and print it to STDOUT
        // Hints:
        //        - You can use the `groupByKey()` method to group the data by key
        //        - You can use the `count()` method to count the number of messages per key
        // todo

        // Task 2: Custom aggregator
        // For most tasks you need to implement your own aggregator. Here we want to calculate the sliding average (https://en.wikipedia.org/wiki/Moving_average)
        // for the current power of our wind turbines. Take a look in the `SlidingAverage` class to check it out how it works.

        // Todo: An initializer is a function that takes no parameters and returns the first (initial) aggregate
        // For example, when you want to count, the initializer should always return 0 (e.g. using the code `() -> 0`)
        // Check the class `SlidingAverage` to see how the initializer could look like
        Initializer<SlidingAverage> initializer = null;
        // Todo: The aggregator takes three parameters: The key of the message to aggregate, the value of the message and the current aggregate
        // For counting you would use something like `(key, value, aggregate) -> aggregate + 1`
        // Hint: Check the class `SlidingAverage` to see how the aggregator could look like
        Aggregator<String, WindTurbineData, SlidingAverage> aggregator = null;
        // Todo: Now its time to aggregate the data.
        // As the aggregator is storing the data in a local state store (and a changelog topic) you need to provide a SerDe for it.
        // Please use for our use case the following code:
        // `Materialized.with(Serdes.String(), new JSONSerde<>(SlidingAverage.class))`
        // Hint: Do not forget to group the data by key and afterwards you need to call the `aggregate()` method
        final KTable<String, SlidingAverage> windTurbinePowerSlidingAverage = null;

        // Write the data from the KTable to the topic `wind-turbine-power-sliding-average`.
        // Do not forget to provide the correct SerDes
        // ??.to("wind-turbine-power-sliding-average",??);

        return builder.build();
    }
}
