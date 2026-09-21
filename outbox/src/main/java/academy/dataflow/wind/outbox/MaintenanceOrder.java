package academy.dataflow.wind.outbox;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A maintenance order for one wind turbine, as the planning service creates it.
 *
 * <p>Serialized as it is, this is also the payload of the event other teams
 * read - the public contract.
 *
 * @param orderId       unique id, also the primary key in the database
 * @param windTurbineId the turbine to service, e.g. {@code arkona-03}
 * @param task          what to do, e.g. {@code gearbox-inspection}
 * @param plannedFor    the day the crew goes out, ISO-8601 {@code YYYY-MM-DD}
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MaintenanceOrder(
        String orderId,
        String windTurbineId,
        String task,
        String plannedFor) {
}
