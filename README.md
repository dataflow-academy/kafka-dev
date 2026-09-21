# Apache Kafka for Developers Training by Anatoly Zelenin: previous labs

This branch keeps the labs of the previous edition of the training. The current
training lives on `main`.

[Learn more about the training](https://zelenin.de/kurse)

| Folder | Topic |
| --- | --- |
| `lab00_hello_world` | Ping and pong: a first producer and consumer |
| `lab01_producer` | Producing wind turbine data |
| `lab02_consumer` | Consuming wind turbine data |
| `lab03_offsets` | Automatic and manual offset commits |
| `lab05_transactions` | Transactions |
| `lab06_connect` | Kafka Connect with a SQLite source database |
| `lab07_hello_streams` | A first Kafka Streams application |
| `lab09_aggregate_streams` | Aggregations with Kafka Streams |
| `lab11_joins` | Joins with Kafka Streams |
| `lab12_time` | Time and windows in Kafka Streams |
| `lab13_schema_management` | Avro and a schema registry |
| `lab_outbox` | The outbox pattern with Debezium |
| `kroxy` | A client for the Kroxylicious proxy lab |

Every Java lab is a Gradle project of its own and brings its own wrapper:

```sh
cd lab00_hello_world
./gradlew build
```

Interested? Contact me at anatoly@zelenin.de
