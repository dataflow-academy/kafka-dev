package wind;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import wind.common.JSONSerde;
import wind.common.KafkaStreamsUtils;
import wind.common.SlidingAverage;

import java.io.IOException;
import java.util.Properties;

public class EnergyApproximator {

    // This is always the same
    public static void main(final String[] args) throws IOException {
        final Properties settings = new Properties();
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "wind-turbine-energy-approximator");
        settings.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        settings.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        KafkaStreamsUtils.runKafkaStreamsApp(getTopology(), settings);
    }

    private static Topology getTopology() {
        StreamsBuilder builder = new StreamsBuilder();

        // Todo Task: Our wind turbines provide us only with point-in-time power production (measured in Watts).
        // To charge our customers, we need to know how much energy (measured in Watt-Hours) these turbine generate.
        // We can approach it the following way: We approximate the energy generation by looking at short time intervals
        // (Here 1 Minute) and calculate the average power produced in this minute (Using our Floating average from Lab 9).


        // As always: Get data from the `wind-turbine-data` topic
        final KStream<String, WindTurbineData> windTurbineData = builder
                .stream("wind-turbine-data",
                        Consumed.with(Serdes.String(), new JSONSerde<>(WindTurbineData.class)));

        // Todo: We need to window the data.
        // Which window-type makes most sense?
        // * Session Windows? (Using SessionWindows)
        // * Tumbling Windows? (Using TimeWindows.??.advanceBy(??))
        // * Sliding windows? (Using TimeWindows)
        // Implement it :)
        // Hint: You need first to group the data before windowing it
        TimeWindowedKStream<String, WindTurbineData> dataPerMinute = null;


        // The Initializer and aggregator is the same as in Lab 9
        Initializer<SlidingAverage> initializer = SlidingAverage::new;
        Aggregator<String, WindTurbineData, SlidingAverage> aggregator = (key, value, aggregate) ->
                aggregate.increment(value.currentPower);

        // Todo: Aggregate the window now
        // Hint: Use the normal aggregate() function. But check the data type! It is a Windowed<String> as a Key
        // Hint: we care only about the last value for the window. We can achieve this by supressing the output using
        //      .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()));
        final KTable<Windowed<String>, SlidingAverage> windowedSlidingAverage = null;

        // Todo: As the output we would like to have a non-windowed KStream and not a windowed KTable.
        // For that, we need to extract the actual Key from the windowed key and also do not forget: We have the now the average power,
        // but we would like to have the generated energy here! As the window is 1 Minute in length, the energy = avg-power / 60
        final KStream<String, WindTurbineEnergyGeneratedData> windTurbineEnergyGenerated = null;

        // Output it to the topic `wind-turbine-energy-generated`

        return builder.build();
    }
}
