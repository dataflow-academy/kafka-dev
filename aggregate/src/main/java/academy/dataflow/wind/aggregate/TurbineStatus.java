package academy.dataflow.wind.aggregate;

/**
 * Operational status of a turbine.
 *
 * <p>Kept in sync with the {@code TurbineStatus} enum in
 * {@code /schemas/wind_turbine_measurement.avsc} so JSON and Avro pipelines
 * speak the same language.
 */
public enum TurbineStatus {
    PRODUCING,
    MAINTENANCE,
    OFFLINE
}
