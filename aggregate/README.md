# Aggregation lab

A Kafka Streams app that keeps the running average power of every wind turbine,
over everything the turbine has ever reported. It reads the telemetry from the
producer lab and writes every change of the average to a compacted topic.

Three TODOs: the state update in
[`TurbinePowerAverage.java`](src/main/java/academy/dataflow/wind/aggregate/TurbinePowerAverage.java),
the aggregation and the output in
[`AggregateApp.java`](src/main/java/academy/dataflow/wind/aggregate/AggregateApp.java).
Everything else — serdes, topic check, lifecycle, logging — is scaffolding and
already works.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
./gradlew run
```

Expects a Kafka cluster on `localhost:9092,9093,9094`, the input topic
`nordwind.scada.public.turbine-telemetry.event` and the output topic
`nordwind.scada.public.turbine-power-average.state`. Topics, application id
and commit interval are constants at the top of `AggregateApp`. Local state
lives in `~/kafka-streams`.

Java 25 or newer.
