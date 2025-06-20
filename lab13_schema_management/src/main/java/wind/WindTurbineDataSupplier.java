package wind;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class WindTurbineDataSupplier implements Supplier<WindTurbineData> {

    private final Random rand = new Random();
    private final int msgsPerSec;
    List<TurbineProperties> turbines = new ArrayList<>();
    List<String> manufacturers = List.of("Vestas", "Siemens", "Gamesa", "Nordex", "Enercon");
    private int msgCount = 0;

    public WindTurbineDataSupplier(int numTurbines, int msgsPerSec) {
        this.msgsPerSec = msgsPerSec;
        for (int i = 0; i < numTurbines; i++) {
            // Vestas V164-7.0 MW specifications
            double maxPower = 7.0 * 1_000_000; // 7 MW = 7,000,000 Watts
            turbines.add(new TurbineProperties(
                    maxPower,
                    "Turbine" + i,
                    "Windpark" + (i % 5),
                    rand.nextDouble(12),
                    manufacturers.get(rand.nextInt(manufacturers.size()))));
        }
    }

    @Override
    public WindTurbineData get() {
        TurbineProperties turbine = turbines.get(rand.nextInt(turbines.size()));

        // Update wind speed with realistic gradual changes
        double windSpeedChange = (rand.nextDouble() - 0.5) * 2.0; // -1 to +1 m/s change
        double newWindSpeed = Math.max(0, Math.min(25, turbine.currentWindSpeed + windSpeedChange));

        // Calculate power output based on wind speed (simplified power curve)
        double newPowerOutput = calculatePowerOutput(newWindSpeed, turbine.maxPower);

        // Add some realistic noise/variation (±5%)
        double noise = 1.0 + (rand.nextDouble() - 0.5) * 0.1;
        newPowerOutput = Math.max(0, Math.min(turbine.maxPower, newPowerOutput * noise));

        // Update turbine wind speed
        turbine.currentWindSpeed = newWindSpeed;

        WindTurbineData windTurbineData = new WindTurbineData(turbine.id,
                turbine.parkId,
                newPowerOutput
        // TODO: Uncomment this for the next exercise
        // , turbine.manufacturer
        );

        if (msgCount >= msgsPerSec && msgsPerSec != -1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            msgCount = 0;
        }
        msgCount++;
        return windTurbineData;
    }

    private double calculatePowerOutput(double windSpeed, double maxPower) {
        // Simplified power curve for wind turbines
        // Cut-in speed: 3 m/s, Rated speed: 12 m/s, Cut-out speed: 25 m/s
        if (windSpeed < 3.0) {
            return 0.0; // Below cut-in speed
        } else if (windSpeed >= 25.0) {
            return 0.0; // Above cut-out speed (turbine stops for safety)
        } else if (windSpeed >= 12.0) {
            return maxPower; // Rated power (12-25 m/s)
        } else {
            // Cubic relationship between wind speed and power (3-12 m/s)
            double normalizedSpeed = (windSpeed - 3.0) / (12.0 - 3.0);
            return maxPower * Math.pow(normalizedSpeed, 3);
        }
    }

    public static class TurbineProperties {
        public final double maxPower;
        public final String id;
        public final String parkId;
        public double currentWindSpeed;
        public final String manufacturer;

        public TurbineProperties(double maxPower, String id, String parkId, double currentWindSpeed,
                String manufacturer) {
            this.maxPower = maxPower;
            this.id = id;
            this.parkId = parkId;
            this.currentWindSpeed = currentWindSpeed;
            this.manufacturer = manufacturer;
        }
    }
}
