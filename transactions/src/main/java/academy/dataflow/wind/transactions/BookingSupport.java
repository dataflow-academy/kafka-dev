package academy.dataflow.wind.transactions;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Scaffolding shared by both booking apps. Nothing to fill in. */
final class BookingSupport {

    private static final Logger log = LoggerFactory.getLogger(BookingSupport.class);

    static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";

    private static final AtomicLong started = new AtomicLong();

    /**
     * Kills the process on the spot while it books transfer number
     * {@code haltAt} of this run - after the first booking was written, before
     * the second. {@code Runtime.halt()} skips shutdown hooks and finally
     * blocks: nothing gets closed, committed or aborted. Just like a power cut.
     *
     * @param haltAt 0 = never
     */
    static void haltIfDue(long haltAt, Producer<?, ?> producer, BankTransfer transfer) {
        if (started.incrementAndGet() != haltAt) {
            return;
        }
        // Make sure the first booking really reached the broker; otherwise the
        // halt would just discard it from the producer's buffer.
        producer.flush();
        log.warn("HALT while booking transfer #{} of this run ({}): the first booking is written, "
                + "the second is not. Set HALT_AT_TRANSFER back to 0 before the next start.",
                haltAt, transfer.transferId());
        Runtime.getRuntime().halt(1);
    }

    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    /** Logs how many transfers were booked, at most every five seconds. */
    static final class Progress {
        private long booked;
        private long reported;
        private long lastReport = System.currentTimeMillis();
        private boolean idleReported = true;

        void countBooked() {
            booked++;
            idleReported = false;
            long now = System.currentTimeMillis();
            if (now - lastReport >= 5000) {
                log.info("{} transfers booked so far", booked);
                reported = booked;
                lastReport = now;
            }
        }

        /** Call after an empty poll. Returns true the first time after new work. */
        boolean idle() {
            if (idleReported) {
                return false;
            }
            idleReported = true;
            if (booked != reported) {
                log.info("{} transfers booked so far - all caught up, waiting for more", booked);
                reported = booked;
                lastReport = System.currentTimeMillis();
            }
            return true;
        }

        long total() {
            return booked;
        }
    }

    private BookingSupport() {
    }
}
