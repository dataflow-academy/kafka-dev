-- The maintenance planning service and its table. Running this file again
-- starts from scratch.

SET client_min_messages = warning;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'planning') THEN
        CREATE ROLE planning LOGIN PASSWORD 'planning';
    END IF;
END
$$;

DROP TABLE IF EXISTS maintenance_order;

CREATE TABLE maintenance_order (
    order_id        uuid PRIMARY KEY,
    wind_turbine_id text NOT NULL,
    task            text NOT NULL,
    planned_for     date NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT ON maintenance_order TO planning;
