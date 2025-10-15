package wind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

public class WindTurbineDataSupplier implements Supplier<WindTurbineData> {

    private final Random rand = new Random();
    private final int msgsPerSec;
    List<TurbineProperties> turbines = new ArrayList<>();
    Map<String, Double> lastPower = new HashMap<>();
    private int msgCount = 0;

    public WindTurbineDataSupplier(int numTurbines, int msgsPerSec) {
        this.msgsPerSec = msgsPerSec;
        for (int i = 0; i < numTurbines; i++) {
            double maxPower = rand.nextDouble(2_000_000, 7_000_000); // Peak power up to 7MW
            String turbineId = "Turbine" + i;
            turbines.add(new TurbineProperties(maxPower, turbineId, "Windpark" + (i % 5)));

            // Initialize with Gaussian-distributed starting power
            double initialPower = Math.max(0, Math.min(maxPower, rand.nextGaussian() * maxPower / 3 + maxPower / 2));
            lastPower.put(turbineId, initialPower);
        }
    }

    @Override
    public WindTurbineData get() {

        TurbineProperties turbine = turbines.get(rand.nextInt(turbines.size()));
        double previousPower = lastPower.get(turbine.id);

        // Generate power change using Gaussian distribution (smaller changes more
        // likely)
        double changeStdDev = turbine.maxPower * 0.1; // Standard deviation is 10% of max power
        double change = rand.nextGaussian() * changeStdDev;
        double newPower = previousPower + change;

        // Clamp power between 0 and maxPower
        newPower = Math.max(0, Math.min(turbine.maxPower, newPower));

        // Store the new power value
        lastPower.put(turbine.id, newPower);

        WindTurbineData windTurbineData = new WindTurbineData(turbine.id, turbine.parkId, newPower);

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

    public record TurbineProperties(
            double maxPower,
            String id,
            String parkId) {
    }
}
