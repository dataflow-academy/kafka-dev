-- The maintenance planning service with its outbox. Running this file again
-- starts from scratch.

\set ON_ERROR_STOP on
SET client_min_messages = warning;

DROP TABLE IF EXISTS maintenance_order;
DROP TABLE IF EXISTS outbox;

CREATE TABLE maintenance_order (
    order_id        uuid PRIMARY KEY,
    wind_turbine_id text NOT NULL,
    task            text NOT NULL,
    planned_for     date NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- TODO 1: create the table outbox. By default, the Debezium Event Router
-- reads the columns id, aggregatetype, aggregateid, type and payload.



-- Everything below is ready.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'planning') THEN
        CREATE ROLE planning LOGIN PASSWORD 'planning';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'debezium') THEN
        CREATE ROLE debezium LOGIN REPLICATION PASSWORD 'debezium';
    END IF;
END
$$;

GRANT SELECT, INSERT ON maintenance_order, outbox TO planning;
GRANT SELECT ON outbox TO debezium;

DROP PUBLICATION IF EXISTS maintenance_outbox;
CREATE PUBLICATION maintenance_outbox FOR TABLE outbox;
