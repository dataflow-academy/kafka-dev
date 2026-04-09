#!/usr/bin/env python3
import time

from confluent_kafka import Producer

num_messages = 50

props = {'bootstrap.servers': 'localhost:9092'}
p = Producer(props)
try:
    for i in range(num_messages):
        msg = "Ping " + str(i)
        p.produce('ping', msg)
        time.sleep(1)
        print("Produced message " + msg)
finally:
    p.flush()
