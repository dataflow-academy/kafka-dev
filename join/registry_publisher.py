#!/usr/bin/env python3
"""Publishes the master data of the fleet to compacted topics.

This is asset management's script, not part of the exercise: it stands in for
the service of another team. It is Python on purpose - that team runs
confluent-kafka (librdkafka), and a different client is exactly the point of
this lab.

Runs once and exits; running it again is harmless, compaction keeps the latest
value per key.
"""

import json
import sys

from confluent_kafka import Producer
from confluent_kafka.admin import AdminClient

BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094"
TURBINE_REGISTRY_TOPIC = "nordwind.assets.public.turbine-registry.state"
# Optional: only published if the topic exists.
PARK_REGISTRY_TOPIC = "nordwind.assets.public.park-registry.state"

# The fleet. Turbine counts and rated power match the simulator in the producer
# lab exactly - the rated power is the denominator of the capacity factor, so
# both sides have to agree on it.
PARKS = [
    ("alpha-ventus", "alpha ventus", 12, "AREVA Wind", "M5000-116", 5000.0, "2010-04-27", "DOTI", "NORTH_SEA"),
    ("nordsee-ost", "Nordsee Ost", 15, "Senvion", "6.2M126", 6200.0, "2015-05-01", "RWE", "NORTH_SEA"),
    ("borkum-riffgrund", "Borkum Riffgrund 1", 10, "Siemens Gamesa", "SWT-4.0-120", 4000.0, "2015-04-01", "Ørsted", "NORTH_SEA"),
    ("arkona", "Arkona", 8, "Siemens Gamesa", "SWT-6.0-154", 6000.0, "2019-04-01", "RWE / Equinor", "BALTIC_SEA"),
    ("baltic-eagle", "Baltic Eagle", 5, "Vestas", "V164-7.0", 7000.0, "2024-01-01", "Iberdrola", "BALTIC_SEA"),
]

PRODUCER_CONFIG = {
    "bootstrap.servers": BOOTSTRAP_SERVERS,
    "client.id": "registry-publisher",
    "acks": "all",
    "enable.idempotence": True,
}


def main() -> None:
    topics = AdminClient({"bootstrap.servers": BOOTSTRAP_SERVERS}).list_topics(timeout=10).topics
    if TURBINE_REGISTRY_TOPIC not in topics:
        sys.exit(f"Topic '{TURBINE_REGISTRY_TOPIC}' is missing - create it first, see the lab text.")
    with_parks = PARK_REGISTRY_TOPIC in topics

    producer = Producer(PRODUCER_CONFIG)
    turbines = 0
    for park_id, name, count, manufacturer, model, rated_power_kw, commissioning_date, operator, sea in PARKS:
        for i in range(1, count + 1):
            turbine_id = f"{park_id}-{i:02d}"
            producer.produce(TURBINE_REGISTRY_TOPIC, key=turbine_id, value=json.dumps({
                "wind_turbine_id": turbine_id,
                "wind_park_id": park_id,
                "manufacturer": manufacturer,
                "model": model,
                "rated_power_kw": rated_power_kw,
                "commissioning_date": commissioning_date,
            }))
            turbines += 1
        if with_parks:
            producer.produce(PARK_REGISTRY_TOPIC, key=park_id, value=json.dumps({
                "wind_park_id": park_id,
                "name": name,
                "operator": operator,
                "sea": sea,
            }))

    remaining = producer.flush(30)
    if remaining:
        sys.exit(f"{remaining} record(s) were not acknowledged - is Kafka reachable at {BOOTSTRAP_SERVERS}?")

    print(f"Published {turbines} turbines to '{TURBINE_REGISTRY_TOPIC}'")
    if with_parks:
        print(f"Published {len(PARKS)} parks to '{PARK_REGISTRY_TOPIC}'")
    else:
        print(f"Topic '{PARK_REGISTRY_TOPIC}' does not exist - parks skipped")


if __name__ == "__main__":
    main()
