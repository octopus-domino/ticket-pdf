CREATE SCHEMA IF NOT EXISTS routing;

DO $$
DECLARE
  table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY['building_history', 'mystic_places'] LOOP
    IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
      IF to_regclass(format('routing.%I', table_name)) IS NOT NULL THEN
        RAISE EXCEPTION 'Table % exists in both public and routing', table_name;
      END IF;
      EXECUTE format('ALTER TABLE public.%I SET SCHEMA routing', table_name);
    END IF;
  END LOOP;
END
$$;
