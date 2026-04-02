#!/usr/bin/env python3

from datatypes import *
import faust

############## Task 1: Join Wind Turbine Data with Master data
# We want to enrich the data of the wind turbines with the master data we have imported in a previous lab from the database.
# Faust does not provide joining functionality out of the box. But we can implement it ourself using the approach
# that Kafka Streams is using. We assume that the masterdata is relatively static and thus, we put it in a Faust-Table.
# Whenever new masterdata arrives, we update the table but we do not perform a join on that data.
# Whenever a new wind turbine data arrives we look the Key up in the Table and if it is found, we process both data points
# and thus, we have a Stream-Table join.
#
# Hint: The masterdata-wind_parks topic contains messages of the form {"id": "0", "name": "Name of Windpark 0"}
#
# We need two agents:
# 1. `fill_table`-Agent: It takes the data from the masterdata topic and updates the table using that data
# 2. `join_*`-Agent: It takes the data from the topic that should be enriched and looks it up in the table and performs the required operation

# todo Init the app
app = None

# todo Init the wind turbine topic
wind_turbine_topic = None

# todo Init the topic masterdata-wind_parks with the WindParkMasterData type. We will use it as a source to fill the table
# To produce a message for debugging purposes you can use the following command:
# echo '{"id": "0", "name": "Name of Windpark 0"}' | kcat -b localhost:9092 -t masterdata-wind_parks -k0
wind_park_masterdata_topic = None

# todo Init the table for the master data
# Hint: masterdata-wind_parks from JDBC Connect often has 1 partition — set partitions=1 on the Table if you see changelog mismatch errors.
wind_park_masterdata_table = None

# todo let us write the joined result to the topic wind-turbine-data-with-wind-park-names. Use the WindTurbineDataWithName record type
wind_turbine_with_wind_park_names_topic = None





# todo Agent 1: write an agent that updates the table with the data from the wind_park_masterdata_topic


# A helper function for the group_by (Hint: In the wind-turbine-data our IDs look like `Windpark1`,
# but in our masterdata topic we use just the number
def wind_park_id_rekey(data: WindTurbineData) -> str:
    return data.wind_park_id.replace("Windpark", "")

# todo Init Agent 2 to read from the wind turbine data topic
async def join_wind_turbines(wind_turbine_data):
    # todo rekey the data using the wind_park_id_rekey function
    rekeyed_wind_turbines = None
    async for key, data in rekeyed_wind_turbines.items():
        # todo print an error message if the Key is not in the table

        # todo otherwise, look it up in the table
        masterdata = None
        # For your convenience ;)
        new_data = WindTurbineDataWithName(wind_turbine_id=data.wind_turbine_id,
                                           wind_park_id=data.wind_park_id,
                                           wind_park_name=masterdata.name,
                                           current_power=data.current_power)
        # todo now, send the data to the wind_turbine_with_wind_park_names_topic. do not forget about the keys

if __name__ == '__main__':
    app.main()
