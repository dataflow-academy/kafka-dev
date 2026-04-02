#!/usr/bin/env python3
import faust

# Initialize the Faust App
app = faust.App(
    'hello_faust', # The name of the app. Faust instances with the same name form a cluster
    broker='kafka://localhost:9092',
    store='rocksdb://', # Where to store the local state (use memory:// only for development. rocksdb:// for production)
    # Optional: disable the built-in web UI if port 6066 is already in use: enable_web=False
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