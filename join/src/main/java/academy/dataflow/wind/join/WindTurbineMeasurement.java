package academy.dataflow.wind.join;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One telemetry measurement from a single wind turbine.
 *
 * <p>Jackson maps the Java camelCase names to snake_case on the wire
 * ({@code wind_turbine_id}, {@code power_kw}, ...).
 *
 * @param windTurbineId unique turbine id, e.g. {@code alpha-ventus-07}
 * @param windParkId    the park this turbine belongs to, e.g. {@code alpha-ventus}
 * @param timestamp     event time in epoch milliseconds (UTC): when the turbine
 *                      measured. Deliberately carried in the payload and NOT
 *                      written into the Kafka record timestamp - the record
 *                      timestamp stays the produce time, so the difference
 *                      between the two is your producing lag.
 * @param windSpeedMs   wind speed at the nacelle in m/s (0-30)
 * @param powerKw       active power output in kW (0-7000; 7 MW is the largest
 *                      turbine in the fleet)
 * @param status        operational status
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WindTurbineMeasurement(
        String windTurbineId,
        String windParkId,
        long timestamp,
        double windSpeedMs,
        double powerKw,
        TurbineStatus status) {
}
