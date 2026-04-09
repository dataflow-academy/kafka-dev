#!/usr/bin/env python3
import sys
import os
sys.path.append(os.path.abspath('../common'))
from bank_transfers_generator import BankTransferGenerator
from confluent_kafka import Producer
import json

props = {'bootstrap.servers': 'localhost:9092',
         'partitioner': 'murmur2_random'}
p = Producer(props)
try:
    for transfer in BankTransferGenerator(1):
        p.produce('bank-transfers', json.dumps(transfer))
        print("{} -> {}: {}€".format(transfer["sender_account"], transfer["receiver_account"], transfer["amount"]))
finally:
    p.flush()
