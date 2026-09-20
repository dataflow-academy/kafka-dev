# Join lab

A Kafka Streams app that joins the telemetry from the producer lab with the
turbine master data and computes a capacity factor for every measurement:
the share of its rated power a turbine delivers right now.

Three TODOs, all in
[`JoinApp.java`](src/main/java/academy/dataflow/wind/join/JoinApp.java): the
table, the join and the capacity factor. Everything else is scaffolding and
already works.

[`registry_publisher.py`](registry_publisher.py) is the master data source: it
publishes the 50 turbines (and, if that topic exists, the 5 parks) once and
exits. It stands in for another team's service, which runs Python — that is the
point of the lab, not an accident. It needs `confluent-kafka`.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
python3 registry_publisher.py   # master data, once
./gradlew run                   # the join
```

Expects a Kafka cluster on `localhost:9092,9093,9094` and the topics
`nordwind.scada.public.turbine-telemetry.event`,
`nordwind.assets.public.turbine-registry.state` and
`nordwind.scada.public.turbine-telemetry-enriched.event`. Topics and
application id are constants at the top of `JoinApp`. Local state lives in
`~/kafka-streams`, the topology is written to `topology.txt`.

Java 25 or newer.
