# Outbox lab

The maintenance planning service again, this time with an outbox: every new
order goes into the table `maintenance_order`, its event into the table
`outbox`, and Debezium's Event Router turns the outbox into a Kafka topic.
The service itself does not talk to Kafka.

Two TODOs, both in
[`OutboxApp.java`](src/main/java/academy/dataflow/wind/outbox/OutboxApp.java):
the insert into the outbox (TODO 1) and the transaction around both writes
(TODO 2). [`outbox.sql`](outbox.sql) brings both tables along. The connector
configuration [`outbox-connector.json`](outbox-connector.json) still lacks the
Event Router.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
psql -f outbox.sql
./gradlew run
```

Expects Postgres on `localhost:5432` with `wal_level=logical`, Kafka Connect
with the Debezium Postgres connector on `localhost:8090`, and a Kafka cluster on
`localhost:9092,9093,9094`. The app uses the database named after your account,
the one `psql` uses without arguments. The connector configuration names that
database `user`; on another machine, change `database.dbname` to your account
name.

Java 25 or newer.
