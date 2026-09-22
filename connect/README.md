# Connect lab

Master data of the wind fleet in Postgres, and two ways to get it into Kafka:
the JDBC Source Connector and Debezium. No Java here — the lab is about what
each connector sees and what it misses.

| File | What |
|---|---|
| [`turbine-registry.sql`](turbine-registry.sql) | the table `turbine_registry` with 50 turbines, and a read-only role for the JDBC connector |
| [`jdbc-source.json`](jdbc-source.json) | JDBC Source Connector |
| [`debezium-source.json`](debezium-source.json) | Debezium Postgres connector |

The lab text on the training platform has the tasks, the hints and the
solutions.

Expects Postgres on `localhost:5432` with `wal_level=logical`, and Kafka
Connect with both connector plugins on `localhost:8090`. The SQL file goes into
the database named after your account, the one `psql` uses without arguments.
The two connector configurations name that database `user`; on another machine,
change `connection.url` and `database.dbname` to your account name.
