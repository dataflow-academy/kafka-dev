#!/usr/bin/env python3
import sys
import os
sys.path.append(os.path.abspath('../common'))
from bank_transfers_generator import BankTransferGenerator
from confluent_kafka import Producer

# TODO: Configure `props` for a transactional producer.
# Hints:
# * How does Kafka know which producer owns the transaction?
# * How to ensure reliable production inside of a transaction?

props = {}
producer = Producer(props)
CREDITS_TOPIC = "credits"
DEBITS_TOPIC = "debits"
try:
    # TODO: Prepare the producer for transactions

    for transfer in BankTransferGenerator(1):
        # TODO: Start a transaction
        suspicious = " <-Suspicious!" if transfer["suspicious"] else ""

        # TODO: Produce two messages: one to `CREDITS_TOPIC`, one to `DEBITS_TOPIC`.
        # Append `suspicious` to the payload string when the flag is set so suspicious transfers are visible in the log.

        print("{} -> {}: {}€".format(transfer["sender_account"], transfer["receiver_account"], transfer["amount"]))

        # For this example we need to flush the messages manually, otherwise we might not see any aborted transactions in the topic
        producer.flush()

        if transfer["suspicious"]:
            # TODO: abort the transaction when it is suspicious

            print("Suspicious transfer between {} and {}! Amount: {}€".format(
                transfer["sender_account"], transfer["receiver_account"], transfer["amount"]))
        else:
            # TODO: commit the transaction
            pass
finally:
    producer.flush()
