# Dual write demo

A maintenance planning service that writes every new order to Postgres and
sends it to Kafka - two writes without a transaction around both.

Nothing to fill in. Run it, kill it between the two writes, and compare the
table with the topic. The lab text on the training platform has the steps.

## Run

```bash
psql -f maintenance-order.sql
./gradlew run
```

Expects Postgres on `localhost:5432` (database `user`) and a Kafka cluster on
`localhost:9092,9093,9094`. Topic, connection and the order of the two writes
are constants at the top of `DualWriteApp`.

Java 25 or newer.
