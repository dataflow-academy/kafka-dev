package wind;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
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

public class AverageWindParkPower {

    // This class is for Lab 10!
    // Important! Please finish the `AverageWindTurbinePower`-Task first ;)

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

        // Now, instead of calculating the sliding average for wind turbines, we want to calculate it for each wind park
        // Thus we would need to group by the wind park ID and not by the wind turbine ID.
        // We call the process repartitioning. Essentially we are changing the partitions for most messages because we are changing the key.
        // There are different ways to approach this:
        // * Using map()
        // * Using selectKey()
        // * Directly using groupBy(…)
        // Todo: Try a few different approaches here

        // TIPP: If you have serialization issues when using map or groupByKey, you need to provide the SerDes to groupBy[Key]:
        // e.g. .groupByKey(Grouped.with(Serdes.String(), WindSerdes.WindTurbineDataSerde()))
        // Question: Why? Have a look at the topology.

        // After you have repartitioned (and grouped) the data you can now aggregate it the same way as in the previous lab
        // please write the data to the topic `wind-park-power-sliding-average`

        return builder.build();
    }
}
