package academy.dataflow.wind.dualwrite;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lab scaffolding — not part of the exercise. It keeps the lab observable and
 * safe to break; you would not write this in a production client.
 */
final class DualWriteSupport {

    private static final Logger log = LoggerFactory.getLogger(DualWriteSupport.class);

    private static final List<String> PARKS = List.of(
            "alpha-ventus:12", "nordsee-ost:15", "borkum-riffgrund:10", "arkona:8", "baltic-eagle:5");
    private static final List<String> TASKS = List.of(
            "gearbox-inspection", "blade-inspection", "oil-change", "yaw-brake-service", "rotor-bolt-check");
    private static final Random random = new Random();

    /** Makes up a maintenance order for one of the 50 turbines of the fleet. */
    static MaintenanceOrder nextOrder() {
        String[] park = PARKS.get(random.nextInt(PARKS.size())).split(":");
        int turbine = 1 + random.nextInt(Integer.parseInt(park[1]));
        return new MaintenanceOrder(
                UUID.randomUUID().toString(),
                "%s-%02d".formatted(park[0], turbine),
                TASKS.get(random.nextInt(TASKS.size())),
                LocalDate.now().plusDays(1 + random.nextInt(30)).toString());
    }

    /** A short name for an order in the log, e.g. {@code order 1a2b3c4d (arkona-03)}. */
    static String logName(MaintenanceOrder order) {
        return "order %s (%s)".formatted(order.orderId().substring(0, 8), order.windTurbineId());
    }

    /**
     * Installs a shutdown hook for SIGTERM and Ctrl+C: it logs, runs {@code stop}
     * and waits until main() has finished the current order. {@code kill -9}
     * still stops the app on the spot.
     */
    static void onShutdown(Runnable stop) {
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!main.isAlive()) {
                return; // main() has already ended, e.g. after an exception
            }
            log.info("Shutdown signal received, finishing the current order ...");
            stop.run();
            try {
                main.join(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));
    }

    private DualWriteSupport() {
    }
}
