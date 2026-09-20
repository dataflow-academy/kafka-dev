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

    /**
     * The measurement plus whatever master data the join found; the capacity
     * factor is still open. In a left join the registration is null.
     */
    static EnrichedMeasurement of(WindTurbineMeasurement m, WindTurbineRegistration r) {
        return new EnrichedMeasurement(m.windTurbineId(), m.windParkId(), m.timestamp(),
                m.windSpeedMs(), m.powerKw(), m.status(),
                r == null ? null : r.manufacturer(),
                r == null ? null : r.model(),
                r == null ? null : r.ratedPowerKw(),
                null);
    }

    /** A copy with the capacity factor filled in. */
    EnrichedMeasurement withCapacityFactor(double capacityFactor) {
        return new EnrichedMeasurement(windTurbineId, windParkId, timestamp, windSpeedMs, powerKw,
                status, manufacturer, model, ratedPowerKw, capacityFactor);
    }
}
