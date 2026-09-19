# Consumer lab

Reads the wind turbine telemetry from Kafka and logs the current power per
wind park every 10 seconds.

Five TODOs in
[`ConsumerApp.java`](src/main/java/academy/dataflow/wind/consumer/ConsumerApp.java):
the configuration, the consumer, the subscription, the poll loop, and what
happens when a record cannot be deserialized. Everything else - the overview,
the rebalance logging, graceful shutdown - is scaffolding and already works.

The overview also remembers the last offset it processed per partition. After
a restart it tells you whether it processes records a second time.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
./gradlew run
```

Expects a Kafka cluster on `localhost:9092,9093,9094` and a producer writing
to `nordwind.scada.public.turbine-telemetry.event`. Both are constants at the
top of `ConsumerApp`.

No producer at hand? `TelemetryFeed` is a finished one:

```bash
./gradlew feed
```

Java 25 or newer.
