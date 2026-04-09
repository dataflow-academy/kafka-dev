#!/usr/bin/env python3
import json

from confluent_kafka import Consumer, Producer, TopicPartition

# Read–process–write inside Kafka transactions.
#
# TODO: Consumer must **not** auto-commit offsets — the producer transaction will commit consumer offsets.
# Hints:
# * `bootstrap.servers`, `group.id`, `auto.offset.reset`
# * `enable.auto.commit` = False
# * Value: JSON dict — use `value.decode('utf-8')` + `json.loads` after poll.
#
# TODO: Producer needs a `transactional.id` and `enable.idempotence`.
# After construction: `init_transactions()` before the loop.
#
# Source topic name must match `bank_transfer_producer.py` (`bank-transfers`).

if __name__ == '__main__':
    # TODO: the consumer must not commit its offsets automatically — where should it start reading?
    consumer_props = {}
    c = Consumer(consumer_props)
    # TODO: configure the producer for transactions (transactional.id, bootstrap.servers, ...)
    producer_props = {}
    p = Producer(producer_props)
    # Source topic
    TRANSFERS_TOPIC = "bank-transfers"
    # target topics
    CREDITS_TOPIC = "credits"
    DEBITS_TOPIC = "debits"

    try:
        # TODO: subscribe to TRANSFERS_TOPIC; call `p.init_transactions()` once before processing.

        while True:
            message = c.poll(100)
            if message is None:
                continue
            if message.error():
                print("Consumer error: {}".format(message.error()))
                continue

            # TODO: parse JSON bank transfer from `message.value()`
            transfer = None
            # TODO: `begin_transaction()` (producer) for this processing step

            suspicious = " <-Suspicious!" if transfer["suspicious"] else ""

            # TODO: produce to both output topics (same pattern as Part 1)

            print("{} -> {}: {}€".format(transfer["sender_account"], transfer["receiver_account"],
                                         transfer["amount"]))

            # TODO: you need consumer group metadata to commit offsets through the producer transaction
            # (Python: `c.consumer_group_metadata()` — check confluent_kafka version/docs).
            group_metadata = None
            # TODO: build the offset map for `send_offsets_to_transaction` / equivalent API.
            # Tips:
            # * `TopicPartition(topic, partition)`
            # * Commit the **next** offset to read, not the last record's offset blindly — see Kafka transaction docs.

            p.flush()

            if transfer["suspicious"]:
                # TODO: abort the transaction
                print("Suspicious transfer between {} and {}! Amount: {}€".format(
                    transfer["sender_account"], transfer["receiver_account"], transfer["amount"]))
            else:
                # TODO: commit producer transaction (which can include consumer offset commits)
                pass

    finally:
        # Cleanup
        c.close()
        p.flush()
