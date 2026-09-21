-- Master data of the wind fleet, as the asset management team keeps it in
-- its own database. Running this file again starts from scratch.

SET client_min_messages = warning;
DROP TABLE IF EXISTS turbine_registry;

CREATE TABLE turbine_registry (
    id              serial PRIMARY KEY,
    wind_turbine_id text NOT NULL UNIQUE,
    wind_park_id    text NOT NULL,
    manufacturer    text NOT NULL,
    model           text NOT NULL,
    rated_power_kw  integer NOT NULL,
    last_edited_by  text NOT NULL DEFAULT current_user,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

INSERT INTO turbine_registry (wind_turbine_id, wind_park_id, manufacturer, model, rated_power_kw)
SELECT format('%s-%s', park, to_char(n, 'FM00')), park, manufacturer, model, rated_power_kw
FROM (VALUES
        (1, 'alpha-ventus',     12, 'AREVA Wind',     'M5000-116',   5000),
        (2, 'nordsee-ost',      15, 'Senvion',        '6.2M126',     6200),
        (3, 'borkum-riffgrund', 10, 'Siemens Gamesa', 'SWT-4.0-120', 4000),
        (4, 'arkona',            8, 'Siemens Gamesa', 'SWT-6.0-154', 6000),
        (5, 'baltic-eagle',      5, 'Vestas',         'V164-7.0',    7000)
     ) AS parks (pos, park, turbines, manufacturer, model, rated_power_kw)
CROSS JOIN LATERAL generate_series(1, turbines) AS n
ORDER BY pos, n;

-- The JDBC Source Connector only needs to read.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jdbc_reader') THEN
        CREATE ROLE jdbc_reader LOGIN PASSWORD 'jdbc_reader';
    END IF;
END
$$;
GRANT SELECT ON turbine_registry TO jdbc_reader;
