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
`nordwind.scada.public.turbine-telemetry.event`.

| Variable | Default |
|---|---|
| `BOOTSTRAP_SERVERS` | `localhost:9092,localhost:9093,localhost:9094` |
| `TOPIC` | `nordwind.scada.public.turbine-telemetry.event` |
| `TICK_INTERVAL_MS` | `1000` — `0` removes the rate limit, which is what the throughput task needs |

Java 21 or newer. The build targets 21 via `options.release`, so it compiles
with whatever JDK is installed.

## Where this leads

This is the training version of `java/producer-json` in the dev templates: same
data model, same wire format, same settings. The template adds what a lab does
not need — configuration from the environment, a Dockerfile, and a README that
explains every setting in place.
