package academy.dataflow.wind.streams;

import static academy.dataflow.wind.streams.StreamsSupport.hostname;

import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Books transfers with Kafka Streams: the same job as the transactional app,
 * without writing the transaction yourself.
 *
 * <p>Everything outside the TODOs is scaffolding and already works. Calls
 * into StreamsSupport are lab helpers you can skip while reading.
 */
public final class StreamsApp {

    private static final Logger log = LoggerFactory.getLogger(StreamsApp.class);

    /** All three bank labs read from this one topic; TransferSource fills it. */
    private static final String TRANSFERS_TOPIC = "nordbank.payments.public.transfer.event";
    /** The bookings stay per lab, so the last lab's result stays readable. */
    private static final String DEBITS_TOPIC = "nordbank.payments.public.debit-streams.event";
    private static final String CREDITS_TOPIC = "nordbank.payments.public.credit-streams.event";
    /** Also the consumer group and the transactional id prefix. */
    private static final String APPLICATION_ID = "nordbank-booking-streams";
    /**
     * The process dies while it processes this transfer (counted from the
     * start of this run). 0 = never.
     */
    private static final long HALT_AT_TRANSFER = 0;
    /** Where the local state stores live, one subdirectory per application.id. */
    private static final String STATE_DIR = System.getProperty("user.home") + "/kafka-streams";

    /** TODO 1: the processing guarantee. */
    private static Properties streamsConfig() {
        Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092,localhost:9093,localhost:9094");
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        props.put(StreamsConfig.CLIENT_ID_CONFIG, hostname());
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
        props.put(StreamsConfig.STATE_DIR_CONFIG, STATE_DIR);
        // Costs nothing and saves a surprise: without it a consumer reads
        // records from aborted transactions too.
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.ISOLATION_LEVEL_CONFIG), "read_committed");

        // TODO 1: a debit without its credit must never become visible.
        // One setting.

        return props;
    }

    private static Topology buildTopology(Properties config) {
        Serde<String> keySerde = Serdes.String();
        Serde<Booking> bookingSerde = JsonSerde.of(Booking.class);

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, BankTransfer> transfers = builder
                .stream(TRANSFERS_TOPIC, Consumed.with(keySerde, JsonSerde.of(BankTransfer.class)))
                // Lab helper: counts, and the process dies here at HALT_AT_TRANSFER.
                .peek(StreamsSupport.countAndHaltAt(HALT_AT_TRANSFER));

        // TODO 2: the debits. Build a Booking for the paying account out of
        // every transfer and write it to DEBITS_TOPIC with
        // Produced.with(keySerde, bookingSerde). The transfer's key already is
        // the paying account.

        // TODO 3: the credits, keyed by the receiving account.

        return builder.build(config);
    }

    public static void main(String[] args) {
        Properties config = streamsConfig();
        // Lab helper: stops here while TODO 1 is still open.
        StreamsSupport.exitIfNoGuarantee(config);
        Topology topology = buildTopology(config);
        // Lab helpers: topology.txt for the visualizer; stops here while TODO 2 or 3 is still open.
        StreamsSupport.publishTopology(topology);
        StreamsSupport.exitIfNotWritingTo(topology, DEBITS_TOPIC, CREDITS_TOPIC);
        log.info("Processing guarantee: {}, halt at transfer: {}",
                config.get(StreamsConfig.PROCESSING_GUARANTEE_CONFIG),
                HALT_AT_TRANSFER == 0 ? "never" : HALT_AT_TRANSFER);
        // Lab helper: starts Streams, closes it cleanly on Ctrl+C or Stop.
        StreamsSupport.runUntilShutdown(new KafkaStreams(topology, config));
    }

    private StreamsApp() {
    }
}
