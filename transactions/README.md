# Transactions lab

Books bank transfers: every transfer becomes a debit on the paying account and
a credit on the receiving one. First by hand with at-least-once, then with a
Kafka transaction around both bookings and the consumer offset.

- [`TransferSource.java`](src/main/java/academy/dataflow/wind/transactions/TransferSource.java)
  writes 1000 transfers per run into the shared transfer topic. Nothing to
  fill in.
- [`AtLeastOnceApp.java`](src/main/java/academy/dataflow/wind/transactions/AtLeastOnceApp.java)
  has one TODO: the two bookings. The consumer commits the offsets itself.
- [`TransactionalApp.java`](src/main/java/academy/dataflow/wind/transactions/TransactionalApp.java)
  has the bookings already; its four TODOs are the transaction around them.

`HALT_AT_TRANSFER` at the top of both apps kills the process between the two
bookings, so you can count what a crash leaves behind.

The lab text on the training platform has the tasks, the hints and the
solutions.

## Run

```bash
./gradlew runTransferSource
./gradlew runAtLeastOnce
./gradlew runTransactional
```

Expects a Kafka cluster on `localhost:9092,9093,9094`. All three bank labs
read from `nordbank.payments.public.transfer.event`; the bookings go to
`nordbank.payments.public.<entity>-at-least-once.event` and
`…-transactions.event`, with `<entity>` being `debit` or `credit`.

Java 25 or newer.
