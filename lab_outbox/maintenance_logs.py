#!/usr/bin/env python3
"""
Exercise: insert maintenance logs and outbox events in one transaction (Outbox pattern).

Complete the marked TODOs — the reference solution is in kafka-dev-solutions.
"""
import random
import time
import uuid
from datetime import datetime, timedelta

import psycopg2
from psycopg2.extras import Json

INSERT_MAINTENANCE_SQL = (
    "INSERT INTO maintenance_logs (turbine_id, date, actions_performed, "
    "next_maintenance_date, maintenance_costs, remarks) "
    "VALUES (%s, CURRENT_TIMESTAMP, %s, CURRENT_TIMESTAMP, %s, %s) "
    "RETURNING maintenance_id"
)

INSERT_OUTBOX_SQL = (
    "INSERT INTO debezium_outbox (aggregatetype, aggregateid, type, payload, payloadid) "
    "VALUES (%s, %s, %s, %s, %s)"
)

DSN = "dbname=user user=user password=password host=localhost port=5432"


def insert_once(cur) -> None:
    turbine_id = random.randint(1, 99)
    actions = ["Oil change", "Blade inspection", "Gearbox replacement"]
    actions_performed = random.choice(actions)
    maintenance_costs = round(random.uniform(1000, 5000), 2)
    remarks = f"Performed by technician #{random.randint(1, 9)}"
    next_maintenance = datetime.now() + timedelta(days=30 * random.randint(1, 12))
    next_maintenance_s = next_maintenance.strftime("%Y-%m-%d %H:%M:%S")

    payload = {
        "turbineId": turbine_id,
        "action": actions_performed,
        "nextMaintenance": next_maintenance_s,
    }

    # TODO: Execute INSERT_MAINTENANCE_SQL, read maintenance_id from RETURNING.
    # TODO: Execute INSERT_OUTBOX_SQL with:
    #   aggregatetype = "WindTurbine", aggregateid = str(turbine_id),
    #   type = "MaintenancePerformed", payload = Json(payload), payloadid = str(uuid.uuid4()).
    raise NotImplementedError("Complete the database inserts (see TODOs).")


def main() -> None:
    inserts_per_second = 1
    delay = 1.0 / inserts_per_second
    conn = psycopg2.connect(DSN)
    conn.autocommit = False
    try:
        while True:
            with conn.cursor() as cur:
                insert_once(cur)
            conn.commit()
            time.sleep(delay)
    except KeyboardInterrupt:
        print("Stopped.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
