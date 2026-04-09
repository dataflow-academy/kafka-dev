#!/usr/bin/env python3
import json

from confluent_kafka import Consumer, Producer, TopicPartition

# Read–process–write inside Kafka transactions.

if __name__ == '__main__':
    # TODO: the consumer must not commit its offsets automatically — where should it start reading?
    consumer_props = {}
    c = Consumer(consumer_props)
    # TODO: configure the producer for transactions
    producer_props = {}
    p = Producer(producer_props)
    # Source topic
    TRANSFERS_TOPIC = "bank-transfers"
    # target topics
    CREDITS_TOPIC = "credits"
    DEBITS_TOPIC = "debits"

    try:
        # TODO: subscribe to TRANSFERS_TOPIC and prepare the producer for transactions

        while True:
            message = c.poll(100)
            if message is None:
                continue
            if message.error():
                print("Consumer error: {}".format(message.error()))
                continue

            # TODO: parse JSON bank transfer from `message.value()`
            transfer = None
            # TODO: Start a transaction

            suspicious = " <-Suspicious!" if transfer["suspicious"] else ""

            # TODO: produce to both output topics (same pattern as Part 1)

            print("{} -> {}: {}€".format(transfer["sender_account"], transfer["receiver_account"],
                                         transfer["amount"]))

            # TODO: you need consumer group metadata to commit offsets through the producer transaction
            group_metadata = None
            # TODO: build the offset map for the producer transaction

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
