#!/usr/bin/env python3
from confluent_kafka import Consumer

# Consumer configuration:
# * `bootstrap.servers`
# * Decode values manually from bytes (UTF-8) below
# * `group.id` — every consumer group needs an id
# * `auto.offset.reset` — what to do when no offsets exist (`earliest` / `latest`)
#
# **Demo only:** auto-commit is disabled here to illustrate manual control — in production services you normally rely on automatic commits or a careful manual strategy.

props = {'bootstrap.servers': 'localhost:9092',
         'group.id': 'notused',
         'enable.auto.commit': False,
         'auto.offset.reset': 'earliest'}
c = Consumer(props)

try:
    c.subscribe(['ping'])
    while True:
        message = c.poll(100)
        if message is None:
            continue
        if message.error():
            print("Consumer error: {}".format(message.error()))
            continue
        print("Received Ping: {}".format(message.value().decode('utf-8')))
finally:
    c.close()
