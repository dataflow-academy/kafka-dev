"""Sample data aligned with the Java WindTurbineDataSupplier (simplified power curve)."""
import random
import time
from typing import Any, Dict, List


class WindTurbineDataSupplier:
    def __init__(self, num_turbines: int, msgs_per_sec: int) -> None:
        self._rand = random.Random()
        self.msgs_per_sec = msgs_per_sec
        self._msg_count = 0
        self._manufacturers = ["Vestas", "Siemens", "Gamesa", "Nordex", "Enercon"]
        self._turbines: List[Dict[str, Any]] = []
        for i in range(num_turbines):
            max_power = 7.0 * 1_000_000
            self._turbines.append(
                {
                    "max_power": max_power,
                    "id": f"Turbine{i}",
                    "park_id": f"Windpark{i % 5}",
                    "wind_speed": self._rand.uniform(0, 12),
                    "manufacturer": self._rand.choice(self._manufacturers),
                }
            )

    def _power_output(self, wind_speed: float, max_power: float) -> float:
        if wind_speed < 3.0:
            return 0.0
        if wind_speed >= 25.0:
            return 0.0
        if wind_speed >= 12.0:
            return max_power
        normalized = (wind_speed - 3.0) / (12.0 - 3.0)
        return max_power * (normalized**3)

    def get(self) -> Dict[str, Any]:
        t = self._turbines[self._rand.randint(0, len(self._turbines) - 1)]
        delta = (self._rand.random() - 0.5) * 2.0
        wind_speed = max(0.0, min(25.0, t["wind_speed"] + delta))
        t["wind_speed"] = wind_speed
        raw_power = self._power_output(wind_speed, t["max_power"])
        noise = 1.0 + (self._rand.random() - 0.5) * 0.1
        power = max(0.0, min(t["max_power"], raw_power * noise))
        return {
            "windTurbineId": t["id"],
            "windParkId": t["park_id"],
            "currentPower": power,
            # TODO (schema evolution): uncomment manufacturer after extending the Avro schema
            # "manufacturer": t["manufacturer"],
        }

    def __iter__(self):
        return self

    def __next__(self) -> Dict[str, Any]:
        value = self.get()
        if self._msg_count >= self.msgs_per_sec and self.msgs_per_sec != -1:
            time.sleep(1)
            self._msg_count = 0
        self._msg_count += 1
        return value
