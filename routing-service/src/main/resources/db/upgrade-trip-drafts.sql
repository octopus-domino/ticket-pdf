CREATE TABLE IF NOT EXISTS routing.journey_trip_drafts (
  owner_id VARCHAR(36) NOT NULL,
  trip_id VARCHAR(64) NOT NULL,
  payload TEXT NOT NULL,
  version BIGINT,
  PRIMARY KEY (owner_id, trip_id)
);

ALTER TABLE routing.journey_trip_drafts
  ADD COLUMN IF NOT EXISTS title VARCHAR(180),
  ADD COLUMN IF NOT EXISTS start_date DATE,
  ADD COLUMN IF NOT EXISTS end_date DATE,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE routing.journey_trip_drafts
SET title = left(payload::jsonb->>'title', 180),
    start_date = NULLIF(payload::jsonb->>'startDate', '')::date,
    end_date = NULLIF(payload::jsonb->>'endDate', '')::date,
    updated_at = CURRENT_TIMESTAMP,
    version = COALESCE(version, 0)
WHERE updated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_trip_drafts_owner_updated
  ON routing.journey_trip_drafts (owner_id, updated_at DESC NULLS LAST, trip_id DESC);
