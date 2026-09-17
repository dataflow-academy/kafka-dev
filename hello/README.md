# Hello lab

Writes one message to the topic `hello` and reads exactly that message back,
by partition and offset.

Nothing to fill in. If it runs, Java, the build and the connection to Kafka
all work.

## Run

```bash
./gradlew run
```

Expects a Kafka cluster on `localhost:9092,9093,9094` and the topic `hello`.

Java 25 or newer.
