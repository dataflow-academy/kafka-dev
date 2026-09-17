# Connect lab

Master data of the wind fleet in Postgres, and two ways to get it into Kafka:
the JDBC Source Connector and Debezium. No Java here - the lab is about what
each connector sees and what it misses.

| File | What |
|---|---|
| [`turbine-registry.sql`](turbine-registry.sql) | the table `turbine_registry` with 50 turbines, and a read-only role for the JDBC connector |
| [`jdbc-source.json`](jdbc-source.json) | JDBC Source Connector |
| [`debezium-source.json`](debezium-source.json) | Debezium Postgres connector |

The lab text on the training platform has the steps.

Expects Postgres on `localhost:5432` (database `user`, `wal_level=logical`)
and Kafka Connect with both connector plugins on `localhost:8090`.
