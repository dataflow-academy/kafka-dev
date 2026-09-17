package academy.dataflow.wind.outbox;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Makes up maintenance orders for the 50 turbines of the fleet.
 *
 * <p>Demo scaffolding, not part of the pattern being practised.
 */
final class MaintenancePlanner {

    private static final List<String> PARKS = List.of(
            "alpha-ventus:12", "nordsee-ost:15", "borkum-riffgrund:10", "arkona:8", "baltic-eagle:5");
    private static final List<String> TASKS = List.of(
            "gearbox-inspection", "blade-inspection", "oil-change", "yaw-brake-service", "rotor-bolt-check");

    private final Random random = new Random();

    MaintenanceOrder next() {
        String[] park = PARKS.get(random.nextInt(PARKS.size())).split(":");
        int turbine = 1 + random.nextInt(Integer.parseInt(park[1]));
        return new MaintenanceOrder(
                UUID.randomUUID().toString(),
                "%s-%02d".formatted(park[0], turbine),
                TASKS.get(random.nextInt(TASKS.size())),
                LocalDate.now().plusDays(1 + random.nextInt(30)).toString());
    }
}
