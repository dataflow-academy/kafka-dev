#!/usr/bin/env python3
import faust

# Hello Faust — minimal stream processing setup.
#
# Hints:
# * `app` name identifies your application; instances with the same name form a cluster.
# * `broker` / `kafka://` — cluster bootstrap.
# * `store` — local state backend (`rocksdb://` for persistence; `memory://` only for quick experiments).
# * Topics can declare `value_type` / serializers; here `greetings` uses raw strings for simplicity.

# Initialize the Faust App
app = faust.App(
    'hello_faust',  # The name of the app. Faust instances with the same name form a cluster
    broker='kafka://localhost:9092',
    store='rocksdb://',  # Where to store the local state (use memory:// only for development. rocksdb:// for production)
)

# Define a topic
greetings_topic = app.topic('greetings', value_type=str, value_serializer='raw')

# Create an agent to read from a certain topic
@app.agent(greetings_topic)
async def greet(greetings):
    # Iterate over all messages in the topic
    async for greeting in greetings:
        # Do something with it
        print(greeting)


if __name__ == '__main__':
    app.main()
