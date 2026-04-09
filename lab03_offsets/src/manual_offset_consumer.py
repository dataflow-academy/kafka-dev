#!/usr/bin/env python3

from confluent_kafka import Consumer

if __name__ == '__main__':
    # TODO: Configure the consumer.
    props = {}
    consumer = None
    try:
        # Todo: Subscribe
        i = 0
        while True:
            # TODO: Poll messages.
            message = None
            if message is None:
                continue
            if message.error():
                print("Consumer error: {}".format(message.error()))
                continue
            # TODO: Decode and print key/value (JSON) like in the consumer lab.

            # TODO: Commit offsets manually — after processing each message, batch, or synchronously vs asynchronously?
            # Questions:
            # * Is it a good idea to commit every single offset?
            # * Commit *before* processing the message or *after*? What happens on crash?
            # * `c.commit(message)` / `c.commit(asynchronous=...)` — check confluent_kafka docs.
    finally:
        c.close()
