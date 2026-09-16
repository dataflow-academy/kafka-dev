# Producer lab

Produces telemetry from a simulated fleet of 50 wind turbines into Kafka.

Four TODOs in
[`ProducerApp.java`](src/main/java/academy/dataflow/wind/producer/ProducerApp.java):
the configuration, the producer, the record, and the failure branch of the
delivery callback. Everything else — the simulator with its power curve, the
tick loop, graceful shutdown, logging — is scaffolding and already works.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
./gradlew run
```

Expects a Kafka cluster on `localhost:9092,9093,9094` and the topic
`nordwind.scada.public.turbine-telemetry.event`. Both, and the tick interval,
are constants at the top of `ProducerApp`.

Java 25 or newer.
