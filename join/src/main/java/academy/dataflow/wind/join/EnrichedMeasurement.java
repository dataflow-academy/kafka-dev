package academy.dataflow.wind.join;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A measurement joined with its turbine's master data - the output of the join.
 *
 * <p>The master data fields and the capacity factor are {@code null} when the
 * join found no master data for the turbine.
 *
 * @param capacityFactor {@code powerKw / ratedPowerKw}: which share of its
 *                       nameplate capacity the turbine delivers right now,
 *                       between 0 and 1. Neither side can compute it alone.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EnrichedMeasurement(
        String windTurbineId,
        String windParkId,
        long timestamp,
        double windSpeedMs,
        double powerKw,
        TurbineStatus status,
        String manufacturer,
        String model,
        Double ratedPowerKw,
        Double capacityFactor) {

    /** Joins one measurement with its registration, which may be null. */
    static EnrichedMeasurement of(WindTurbineMeasurement m, WindTurbineRegistration r) {
        if (r == null) {
            return new EnrichedMeasurement(m.windTurbineId(), m.windParkId(), m.timestamp(),
                    m.windSpeedMs(), m.powerKw(), m.status(), null, null, null, null);
        }
        // TODO 3: compute the capacity factor, rounded to three decimals.
        Double capacityFactor = null;
        return new EnrichedMeasurement(m.windTurbineId(), m.windParkId(), m.timestamp(),
                m.windSpeedMs(), m.powerKw(), m.status(),
                r.manufacturer(), r.model(), r.ratedPowerKw(), capacityFactor);
    }

    /** Feeds one known measurement through of() and compares. */
    static String selfCheck() {
        WindTurbineMeasurement m = new WindTurbineMeasurement(
                "nordsee-ost-01", "nordsee-ost", 0, 9.0, 1234.5, TurbineStatus.PRODUCING);
        WindTurbineRegistration r = new WindTurbineRegistration(
                "nordsee-ost-01", "nordsee-ost", "Senvion", "6.2M126", 6200.0, "2015-05-01");
        Double capacityFactor = of(m, r).capacityFactor();
        if (capacityFactor == null) {
            return "TODO 3 is still open";
        }
        return capacityFactor == 0.199 ? null
                : "The capacity factor is not right yet: 1234.5 kW of 6200 kW should give 0.199, but gave "
                        + capacityFactor;
    }
}
