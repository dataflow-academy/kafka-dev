package academy.dataflow.wind.join;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Master data for one wind turbine, keyed by the turbine id in
 * {@code nordwind.assets.public.turbine-registry.state}.
 *
 * @param windTurbineId     unique turbine id, e.g. {@code arkona-03} - the same
 *                          id the telemetry uses as its key
 * @param windParkId        the park, a reference into the park registry
 * @param manufacturer      e.g. {@code Siemens Gamesa}
 * @param model             e.g. {@code SWT-6.0-154}
 * @param ratedPowerKw      nameplate capacity in kW
 * @param commissioningDate grid connection, ISO-8601 {@code YYYY-MM-DD}
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record WindTurbineRegistration(
        String windTurbineId,
        String windParkId,
        String manufacturer,
        String model,
        double ratedPowerKw,
        String commissioningDate) {
}
