CREATE TABLE IF NOT EXISTS workflows (
  slug VARCHAR(48) PRIMARY KEY,
  owner_id VARCHAR(120) NOT NULL,
  draft TEXT NOT NULL,
  published TEXT,
  source TEXT,
  shared BOOLEAN NOT NULL DEFAULT FALSE,
  revision BIGINT NOT NULL DEFAULT 0,
  published_revision BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  lock_version BIGINT
);
CREATE INDEX IF NOT EXISTS ix_workflow_owner ON workflows(owner_id);
CREATE TABLE IF NOT EXISTS site_addresses (
  slug VARCHAR(48) PRIMARY KEY,
  kind VARCHAR(16) NOT NULL,
  version BIGINT
);
INSERT INTO site_addresses(slug,kind,version)
SELECT slug,'site',0 FROM journey_sites s WHERE NOT EXISTS (SELECT 1 FROM site_addresses a WHERE a.slug=s.slug);
INSERT INTO site_addresses(slug,kind,version)
SELECT slug,'workflow',0 FROM workflows w WHERE NOT EXISTS (SELECT 1 FROM site_addresses a WHERE a.slug=w.slug);
