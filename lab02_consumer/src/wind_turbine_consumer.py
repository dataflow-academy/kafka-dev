#!/usr/bin/env python3
import json

from confluent_kafka import Consumer

import wind_turbine_api

if __name__ == '__main__':
    # TODO: Configure the consumer (`props` dict).
    # Hints:
    # * How to connect to Kafka?
    # * How to make the consumer part of a group?
    # * What to do when there is no committed offset for this group?
    #
    # We want to read *all* messages already on the topic when the group starts — pick `auto.offset.reset` accordingly.

    props = {}
    # TODO: Create the consumer
    consumer = None
    # Small HTTP helper on port 8989 to inspect the latest measurements (see `wind_turbine_api.py`).
    wind_turbine_api.start()
    try:
        # TODO: Subscribe to `wind-turbine-data` (or use `assign()` only in the extra exercise).
        # Hint: `consumer.subscribe([TOPIC])`

        while True:
            # TODO: Poll for messages (`consumer.poll(timeout)` — timeout in seconds as float).
            message = None
            # `poll` may return None when no message arrived in time.
            if message is None:
                continue
            # There might be an error record (e.g. partition EOF) — handle it.
            if message.error():
                print("Consumer error: {}".format(message.error()))
                continue
            # TODO: Key — Kafka delivers bytes; decode to `str` for the turbine id.
            key = None
            # TODO: Value — decode bytes to UTF-8, then `json.loads` into a dict (WindTurbineData JSON).
            data = None
            # Push to the small API for visualization
            wind_turbine_api.add_measurement(key, data)
            print("{}: {}".format(key, data))
    finally:
        consumer.close()
