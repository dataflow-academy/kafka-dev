#!/usr/bin/env python3
import faust

# TODO: Initialize the Faust application.
# Hints:
# * how to identify the application?
# * how to connect to the cluster?
# * where to store the state?
app = None

# We define the record type that is present in the topic. Use JSON as a (de)serializer
class WindTurbineData(faust.Record, serializer='json'):
    wind_turbine_id: str
    wind_park_id: str
    current_power: float


############## TASK 1: Transforming data

# TODO: Create an agent that reads from the `wind-turbine-data` topic.
# There are two ways to write data to a topic:
# 1. either explicitly by `topic.send()`
# 2. or by defining a sink in the agent and use the `yield` keyword
# try out both ways
async def analyze(wind_turbine_data):
    # iterate over the data
    async for data in wind_turbine_data:
        # Convert Watts to Mega-Watts
        data.current_power = data.current_power / 1_000_000
        # TODO: write the data to the `wind-turbine-data-in-mw` topic

############## TASK 2: Routing Data
# We read the data again from the wind-turbine topic and based on the current power we write it either to the topic
# much-wind (when the current power is larger > 10000 W)
# or little-wind (when the current power is smaller than 10000W)
#
# Hints:
# * Define destination topics with `app.topic(...)`.
# * Route by thresholds on **Watts** before you scale to MW, or stay consistent with your Task 1 pipeline.
# * Model filtering and branching with separate topics (e.g. `little-wind`, `much-wind`).

# TODO: define both topics

# TODO: define the agent
async def split(wind_turbine_data):
    pass
    # TODO: iterate over the data (do not forget about keys and values)
    # send the data to the little_wind topic (do not forget about the key! it should stay the same)
    # or to the much_wind topic depending on the current power


if __name__ == '__main__':
    app.main()
