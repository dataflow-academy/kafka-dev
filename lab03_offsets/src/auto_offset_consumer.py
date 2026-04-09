#!/usr/bin/env python3

from confluent_kafka import Consumer

# Automatic offset commits with a configurable interval (librdkafka handles the commits).
#
# TODO (lab): Set automatic commit interval to **10 seconds** (milliseconds).
# Hint: `auto.commit.interval.ms` (see also Apache Kafka consumer configuration reference).

if __name__ == '__main__':
    props = {
        'bootstrap.servers': 'localhost:9092',
        'group.id': 'offsets-lab-auto',
        'auto.offset.reset': 'earliest',
        'enable.auto.commit': True,
        # TODO: change this to 10_000 for the exercise
        'auto.commit.interval.ms': 5000,
    }

    TOPIC = 'wind-turbine-data'

    c = Consumer(props)
    try:
        c.subscribe([TOPIC])
        print('Started')
        while True:
            msg = c.poll(0.1)
            if msg is None:
                continue
            if msg.error():
                print('Consumer error: {}'.format(msg.error()))
                continue
            key = msg.key().decode('utf-8') if msg.key() else None
            value = msg.value().decode('utf-8') if msg.value() else None
            print('{}: {}'.format(key, value))
    finally:
        c.close()
