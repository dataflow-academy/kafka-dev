package academy.dataflow.wind.producer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Simulates a fleet of 50 wind turbines across 5 wind parks with plausible
 * physics.
 *
 * <p>Demo scaffolding, not part of the Kafka patterns being practised.
 *
 * <p>The model, in brief:
 * <ul>
 *   <li><b>Wind</b>: each park has its own wind speed following a bounded
 *       random walk; turbines in the same park see the park wind plus a small
 *       local jitter. Correlated wind is what makes park-level aggregations
 *       (a later lab) meaningful.</li>
 *   <li><b>Power curve</b>: 0 below the cut-in speed (3 m/s), a cubic ramp up
 *       to the rated wind speed (12 m/s) - power in the ramp grows with the
 *       cube of the wind speed - then rated power until the cut-out speed
 *       (25 m/s), and 0 above it (storm shutdown).</li>
 *   <li><b>Fleet</b>: one turbine model per park (see PARK_MODEL_RATED_KW),
 *       4.0 to 7.0 MW; 7 MW is roughly the largest current Vestas platform
 *       and is never exceeded.</li>
 *   <li><b>Maintenance</b>: occasionally a turbine goes into MAINTENANCE for a
 *       few minutes and produces nothing.</li>
 * </ul>
 */
final class WindParkSimulator {

    private record Turbine(String id, String parkId, double ratedPowerKw) {
    }

    private static final double CUT_IN_MS = 3.0;
    private static final double RATED_MS = 12.0;
    private static final double CUT_OUT_MS = 25.0;
    private static final double MAX_WIND_MS = 30.0;

    /** Park name -> number of turbines. 50 in total, parks differ in size. */
    private static final Map<String, Integer> PARKS = Map.of(
            "alpha-ventus", 12,
            "nordsee-ost", 15,
            "borkum-riffgrund", 10,
            "arkona", 8,
            "baltic-eagle", 5);

    /**
     * Park name -> rated power (kW) of the ONE turbine model installed there.
     *
     * <p>Real offshore parks are built out with a single turbine model, so
     * rated power is a property of the park, not a per-turbine coin flip. That
     * is also what makes this table a CONTRACT rather than decoration: the
     * turbine master data carries exactly these numbers as
     * {@code rated_power_kw}, and a join divides the measured power by them to
     * get a capacity factor. Roll the rated power randomly here and that
     * capacity factor exceeds 1.0 - a physically impossible number, produced
     * by a join whose two sides disagree about the world. Change a value here,
     * change it in the master data too.
     *
     * <p>The models behind the numbers (the real machines in these parks):
     * <pre>
     *   alpha-ventus     AREVA M5000-116        5.0 MW
     *   nordsee-ost      Senvion 6.2M126        6.2 MW
     *   borkum-riffgrund Siemens SWT-4.0-120    4.0 MW
     *   arkona           Siemens SWT-6.0-154    6.0 MW
     *   baltic-eagle     Vestas V164-7.0        7.0 MW  (the real park runs the
     *                    V174-9.5; capped here to the 7 MW fleet maximum)
     * </pre>
     * Fleet total: 276 MW installed.
     */
    private static final Map<String, Double> PARK_MODEL_RATED_KW = Map.of(
            "alpha-ventus", 5000.0,
            "nordsee-ost", 6200.0,
            "borkum-riffgrund", 4000.0,
            "arkona", 6000.0,
            "baltic-eagle", 7000.0);

    private final Random random = new Random();
    private final List<Turbine> turbines = new ArrayList<>();
    private final Map<String, Double> parkWindMs = new HashMap<>();
    /** Turbine id -> end of the current maintenance window. */
    private final Map<String, Instant> inMaintenanceUntil = new HashMap<>();

    WindParkSimulator() {
        PARKS.forEach((parkId, count) -> {
            // Every park starts with its own wind situation.
            parkWindMs.put(parkId, 4 + random.nextDouble() * 10);
            for (int i = 1; i <= count; i++) {
                String turbineId = "%s-%02d".formatted(parkId, i);
                turbines.add(new Turbine(turbineId, parkId, PARK_MODEL_RATED_KW.get(parkId)));
            }
        });
    }

    /** Advances the simulation by one tick and returns one measurement per turbine. */
    List<WindTurbineMeasurement> nextTick(Instant now) {
        // Advance each park's wind: bounded random walk, occasionally gusty.
        parkWindMs.replaceAll((park, wind) ->
                clamp(wind + random.nextGaussian() * 0.4, 0.0, MAX_WIND_MS));

        List<WindTurbineMeasurement> measurements = new ArrayList<>(turbines.size());
        for (Turbine turbine : turbines) {
            double windMs = clamp(
                    parkWindMs.get(turbine.parkId()) + random.nextGaussian() * 0.5,
                    0.0, MAX_WIND_MS);

            TurbineStatus status = nextStatus(turbine, now);
            double powerKw = status == TurbineStatus.PRODUCING
                    ? powerAt(windMs, turbine.ratedPowerKw())
                    : 0.0;

            measurements.add(new WindTurbineMeasurement(
                    turbine.id(),
                    turbine.parkId(),
                    now.toEpochMilli(),
                    round1(windMs),
                    round1(powerKw),
                    status));
        }
        return measurements;
    }

    private TurbineStatus nextStatus(Turbine turbine, Instant now) {
        Instant until = inMaintenanceUntil.get(turbine.id());
        if (until != null) {
            if (now.isBefore(until)) {
                return TurbineStatus.MAINTENANCE;
            }
            inMaintenanceUntil.remove(turbine.id());
        }
        // Rare event: ~1 in 20,000 ticks per turbine, lasting 1-5 minutes.
        if (random.nextDouble() < 0.00005) {
            inMaintenanceUntil.put(turbine.id(), now.plusSeconds(60 + random.nextInt(240)));
            return TurbineStatus.MAINTENANCE;
        }
        return TurbineStatus.PRODUCING;
    }

    /** The idealized power curve of a pitch-controlled turbine. */
    private double powerAt(double windMs, double ratedKw) {
        if (windMs < CUT_IN_MS || windMs >= CUT_OUT_MS) {
            // Below cut-in there is not enough wind; above cut-out the turbine
            // pitches out of the wind to protect itself (storm shutdown).
            return 0.0;
        }
        if (windMs >= RATED_MS) {
            // Between rated and cut-out speed the controller caps at rated power.
            return ratedKw;
        }
        // In the partial-load range the power in the wind grows with the cube
        // of the wind speed.
        double fraction = (windMs - CUT_IN_MS) / (RATED_MS - CUT_IN_MS);
        double powerKw = ratedKw * Math.pow(fraction, 3);
        // A little measurement noise, never exceeding rated power.
        return clamp(powerKw * (1 + random.nextGaussian() * 0.02), 0.0, ratedKw);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
