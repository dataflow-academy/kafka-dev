package wind;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import wind.common.JSONSerde;
import wind.common.KafkaStreamsUtils;
import wind.datatypes.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

public class WindTurbineJoiner {

    public static void main(final String[] args) throws IOException {
        final Properties settings = new Properties();
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "wind-turbine-joiner");
        settings.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        settings.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        KafkaStreamsUtils.runKafkaStreamsApp(getTopology(), settings);
    }

    private static Topology getTopology() {
        StreamsBuilder builder = new StreamsBuilder();

        // One Serde to rule them all
        Serde<String> keySerde = Serdes.String();

        /** Hints
         * Try to understand the types
         * Always use Serdes even if they are optional
         * Use the visualizer to understand the topology
         */


        /************************************************************************************************
         * Task 1: KSTream - KTable
         * wind-turbine-data with wind-park-masterdata
         ************************************************************************************************/

        // Left: Wind Turbine Data (KStream)

        // Define the Serdes for the data types used in this join. Makes it easier to read the code.
        Serde<WindTurbineData> turbineDataSerde = new JSONSerde<>(WindTurbineData.class);

        final KStream<String, WindTurbineData> windTurbineData = builder
                .stream("wind-turbine-data",
                        Consumed.with(keySerde, turbineDataSerde));

        // Right: Wind Turbine Master Data (KTable)

        // Serde for the right side
        Serde<WindTurbineMasterData> masterDataSerde = new JSONSerde<>(WindTurbineMasterData.class);

        final KTable<String, WindTurbineMasterData> windTurbineMasterdata = builder
                .table("wind-turbine-masterdata",
                        Consumed.with(keySerde, masterDataSerde));

        // Result Serde
        Serde<WindTurbineDataWithMasterdata> turbineDataWithMasterdataSerde = new JSONSerde<>(WindTurbineDataWithMasterdata.class);

        // Join: KStream - KTable -> KStream
        // joiner = (leftValue, rightValue) -> resultValue
        final ValueJoiner<WindTurbineData, WindTurbineMasterData, WindTurbineDataWithMasterdata> joiner = null;
        // Join-Serdes: Joined.with(keySerde, leftValueSerde, rightValueSerde)

        // left.join(right, joiner, join-serdes)
        final KStream<String, WindTurbineDataWithMasterdata> enrichedWindTurbineData = null;

        enrichedWindTurbineData.print(Printed.toSysOut());
        enrichedWindTurbineData.to("wind-turbine-data-with-wind-park-names",
                Produced.with(keySerde, turbineDataWithMasterdataSerde));


        /************************************************************************************************
         * Task 2: KTable - KTable
         * wind-turbine-status with wind-turbine-masterdata
         ************************************************************************************************/

        /*
        Serde<WindTurbineStatus> statusSerde = new JSONSerde<>(WindTurbineStatus.class);
        final KTable<String, WindTurbineStatus> windTurbineStatus = builder
                .table("wind-turbine-status",
                        Consumed.with(keySerde, statusSerde));

        Serde<WindTurbineStatusWithMasterdata> enrichedWindTurbineSerde = null;
        // A Table-Table Join does not need a Joined.with() call.
        final KTable<String, WindTurbineStatusWithMasterdata> enrichedWindTurbineStatus = null;

        enrichedWindTurbineStatus.toStream().to("wind-turbine-status-with-masterdata",
                Produced.with(keySerde, enrichedWindTurbineSerde));
        /**/

        /************************************************************************************************
         * Task 3: KStream - KStream
         * wind-turbine-data with weather-data
         */

        /*
        Serde<WeatherData> weatherDataSerde = new JSONSerde<>(WeatherData.class);
        final KStream<String, WeatherData> weatherData = builder
                .stream("weather-data",
                        Consumed.with(keySerde, weatherDataSerde));

        Serde<WindTurbineDataWithWeatherData> turbineDataWithWeatherDataSerde = new JSONSerde<>(WindTurbineDataWithWeatherData.class);

        // A Stream-Stream Join is always time-windowed. We need to define the window duration.
        Duration joinWindowDuration = Duration.ofSeconds(20);

        // left.join(right, joiner, join-window, join-serdes)
        // join-window = JoinWindows.ofTimeDifferenceWithNoGrace(joinWindowDuration)
        // join-serdes = StreamJoined.with(keySerde, leftValueSerde, rightValueSerde)
        final KStream<String, WindTurbineDataWithWeatherData> enrichedWindTurbineDataWithWeatherData = null;

        enrichedWindTurbineDataWithWeatherData.to("wind-turbine-data-with-weather-data",
                Produced.with(keySerde, turbineDataWithWeatherDataSerde));

        /**/

        return builder.build();
    }
}
