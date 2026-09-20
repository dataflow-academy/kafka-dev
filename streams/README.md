# Streams lab

Books the same bank transfers as the transactions lab, this time with Kafka
Streams: a debit and a credit per transfer, exactly once.

Three TODOs in
[`StreamsApp.java`](src/main/java/academy/dataflow/wind/streams/StreamsApp.java):
the processing guarantee, the debits and the credits. `HALT_AT_TRANSFER` at the
top kills the process mid-run, so you can count what a crash leaves behind.

The transfers come from `TransferSource` in the `transactions` folder.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
./gradlew run
```

Expects a Kafka cluster on `localhost:9092,9093,9094` and the topics
`nordbank.payments.public.transfer-streams.event`,
`nordbank.payments.public.debit-streams.event` and
`nordbank.payments.public.credit-streams.event`.

Java 25 or newer.
