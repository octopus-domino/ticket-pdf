CREATE EXTENSION IF NOT EXISTS postgis;
CREATE TABLE IF NOT EXISTS building_history (
    id BIGSERIAL PRIMARY KEY, osm_id VARCHAR(50), address TEXT,
    construction_year INTEGER, architect VARCHAR(300), style VARCHAR(100), heritage_status VARCHAR(100),
    source VARCHAR(50) NOT NULL, source_url TEXT, geometry GEOMETRY(GEOMETRY,4326),
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_building_history_geometry ON building_history USING GIST(geometry);
CREATE INDEX IF NOT EXISTS idx_building_history_geography ON building_history USING GIST((geometry::geography));
CREATE INDEX IF NOT EXISTS idx_building_history_osm_id ON building_history(osm_id);
CREATE INDEX IF NOT EXISTS idx_building_history_year ON building_history(construction_year);
CREATE UNIQUE INDEX IF NOT EXISTS uq_building_history_source_osm ON building_history(source,osm_id)
    WHERE source='HOITH' AND osm_id IS NOT NULL;
