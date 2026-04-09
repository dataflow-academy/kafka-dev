#!/usr/bin/env python3
from wind_turbine_data_gen import WindTurbineDataGen
from confluent_kafka import Producer
import json

# TODO: Configure the producer (`props` dict for confluent_kafka.Producer).
# Hints:
# * How to connect to Kafka?
# * What do you need to do to make the producer compatible with the Java producer?
# * How to improve the reliability of the producer?
# * How to improve the performance of the producer?

props = {}
# TODO: Create the producer
producer = None
try:
    for data in WindTurbineDataGen(50, 1):
        # TODO: Produce JSON to the topic `wind-turbine-data`.
        # The record key should be the wind turbine id (string), the value JSON for the dict `data`.
        print("Produced data for wind turbine " + data["wind_turbine_id"])
finally:
    # Always flush before exit so buffered messages are sent.
    p.flush()
