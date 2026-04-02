#!/usr/bin/env python3
from datetime import timedelta, datetime

import faust

from datatypes import *

################ Task: Approximate generated energy
# Our wind turbines provide only the current power. But they are billed by Wh (watt-hour).
# Thus, we need to approximate the energy generated somehow.
# For this, we calculate the average energy generated in 10s-intervals.
#
# This is done using windowed tables in Faust. They are like normal tables, but hold for each window the relevant data.
# When the window closes we take the data in the window, calculate the energy and output it to a topic for further processing

# todo Init the app
app = None

# By default, Faust checks the table windows every 30s. But we do not want to wait so long.
# Reduce it to 1 second
app.conf.table_cleanup_interval = 1

# todo (preferably as one of the last things you do here)
# the table (defined below) consists of Keys of the Form (KEY, (window_start_timestamp, window_end_timestamp))
# The values are the SlidingAverages used in previous labs
def window_processor(key, sliding_average: SlidingAverage):
    # get the key (window key is (turbine_id, (window_start, window_end)))
    real_key, (start_timestamp, end_timestamp) = key
    # unpack the sliding average
    mean = sliding_average.average
    # transform timestamps to dates
    start_date = datetime.fromtimestamp(start_timestamp)
    end_date = datetime.fromtimestamp(end_timestamp)
    # calculate the energy (the unit is what*hours – so if we have a 10s interval,
    # we need to divide it by 360 because an hour has 3600 seconds)
    energy = mean / 360

    # todo: optional: Write it also out to a topic
    # print it
    print(
        f'{real_key} generated {energy:.2f} Wh '
        f'between {start_date.strftime("%H:%M:%S")} and {end_date.strftime("%H:%M:%S")}')


# todo Init the wind turbine topic
wind_turbine_topic = None

# todo create a table for the windowed SlidingAverage
# Hints:
# * the window_processor() should be called when the window closes
# * We should use a tumbling window (what's that?) with a 10s interval that expires after 10 more seconds
# * If `wind-turbine-data` has only one partition, pass partitions=1 on the Table so the changelog matches.
windowed_power_table = None


# todo define the agent
async def aggregate_windowed_power(wind_turbine_data):
    async for key, value in wind_turbine_data.items():
        # If we lookup a key in the table we do not get a single value, but a window-set.
        window_set = None
        # todo Checkout which function to use to get the current (previous) entry from the table that can be updated
        # next update the average and put it back in the table
        prev_avg = None
        new_avg = None
        # ??
        print("Updated Key {} prev_count: {}, new_count: {}".format(key, prev_avg.count, new_avg.count))


if __name__ == '__main__':
    app.main()
