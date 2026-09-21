package academy.dataflow.wind.aggregate;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * The running average power of one wind turbine - the state of the
 * aggregation, and the value written to the output topic.
 *
 * <p>Jackson maps the names to snake_case on the wire ({@code wind_turbine_id},
 * {@code sum_kw}, {@code avg_kw}, ...).
 *
 * @param windTurbineId the turbine this average belongs to
 * @param count         how many measurements went into the average
 * @param sumKw         sum of their power output in kW
 * @param avgKw         the average power output in kW
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TurbinePowerAverage(
        String windTurbineId,
        long count,
        double sumKw,
        double avgKw) {

    /** The state before the first measurement. */
    static TurbinePowerAverage empty() {
        return new TurbinePowerAverage(null, 0, 0.0, 0.0);
    }

    /**
     * Returns the state after one more measurement. Records are immutable:
     * build a new one instead of changing this one.
     */
    TurbinePowerAverage add(WindTurbineMeasurement measurement) {
        // TODO 1: return the new state. Count, sum and average all change.
        throw new UnsupportedOperationException("TODO 1 is still open");
    }
}
