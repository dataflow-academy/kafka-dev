package wind;

public class WindTurbineEnergyGeneratedData {
    public String windTurbineId;
    public double generatedEnergyInWh;

    public WindTurbineEnergyGeneratedData(String windTurbineId, double generatedEnergyInWh) {
        this.windTurbineId = windTurbineId;
        this.generatedEnergyInWh = generatedEnergyInWh;
    }

    @Override
    public String toString() {
        return "WindTurbineEnergyGeneratedData{" +
                "windTurbineId='" + windTurbineId + '\'' +
                ", generatedEnergyInWh=" + generatedEnergyInWh +
                '}';
    }
}
