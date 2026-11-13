CREATE EXTENSION IF NOT EXISTS postgis;
CREATE TABLE IF NOT EXISTS mystic_places (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    region VARCHAR(100),
    city VARCHAR(100),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -85 AND 85),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude > -180 AND longitude < 180),
    category VARCHAR(50),
    description TEXT,
    source_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_mystic_place UNIQUE(name, latitude, longitude)
);
-- Match the geography expression used by ST_DWithin (a geometry-only index cannot serve it).
CREATE INDEX IF NOT EXISTS idx_mystic_places_geog ON mystic_places USING GIST (
    (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography)
);
