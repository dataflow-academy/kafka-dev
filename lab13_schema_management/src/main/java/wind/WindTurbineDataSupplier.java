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
            turbines.add(new TurbineProperties(rand.nextDouble(5000, 1.5 * 1000 * 1000),
                    "Turbine" + i,
                    "Windpark" + (i % 5)));
        }
    }

    @Override
    public WindTurbineData get() {

        TurbineProperties turbine = turbines.get(rand.nextInt(turbines.size()));

        WindTurbineData windTurbineData = new WindTurbineData(
                turbine.id,
                turbine.parkId,
                rand.nextDouble(0, turbine.maxPower),
                // TODO: Uncomment this for the next exercise
                // manufacturers.get(rand.nextInt(manufacturers.size()))
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

    public record TurbineProperties(
            double maxPower,
            String id,
            String parkId) {
    }
}
