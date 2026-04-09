#!/usr/bin/env python3
from confluent_kafka import Consumer
import wind_turbine_api
import json

# TODO: Configure the consumer
# Hints:
# * How to connect to Kafka?
# * How to make the consumer part of a group?
# * What to do when there is no committed offset for this group?
#
# We want to read *all* messages already on the topic when the group starts — pick `auto.offset.reset` accordingly.

props = {}
# TODO: Create the consumer
# Check the documentation: https://docs.confluent.io/platform/current/clients/confluent-kafka-python/html/index.html#pythonclient-consumer
consumer = None
# Small HTTP helper on port 8989 to inspect the latest measurements (see `wind_turbine_api.py`).
wind_turbine_api.start()
try:
    # TODO: Subscribe to `wind-turbine-data`

    while True:
        # TODO: Poll for messages (100ms timeout is usually ok)
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
