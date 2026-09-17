# Outbox lab

The maintenance planning service again, this time with an outbox: every new
order goes into the table `maintenance_order`, its event into the table
`outbox`, and Debezium's Event Router turns the outbox into a Kafka topic.
The service itself does not talk to Kafka.

Three TODOs: the outbox table in [`outbox.sql`](outbox.sql) (TODO 1), the
insert into it and the transaction around both writes in
[`OutboxApp.java`](src/main/java/academy/dataflow/wind/outbox/OutboxApp.java)
(TODO 2 and 3). The connector configuration
[`outbox-connector.json`](outbox-connector.json) still lacks the Event Router.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
psql -f outbox.sql
./gradlew run
```

Expects Postgres on `localhost:5432` (database `user`, `wal_level=logical`),
Kafka Connect with the Debezium Postgres connector on `localhost:8090`, and a
Kafka cluster on `localhost:9092,9093,9094`.

Java 25 or newer.
