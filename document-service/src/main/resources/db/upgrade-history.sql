CREATE TABLE IF NOT EXISTS resource_revisions (
  id VARCHAR(255) PRIMARY KEY,user_id VARCHAR(255) NOT NULL,kind VARCHAR(16) NOT NULL,
  resource_id VARCHAR(255) NOT NULL,content_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,payload CLOB NOT NULL,payload_bytes INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_revision_owner ON resource_revisions(user_id);
CREATE INDEX IF NOT EXISTS idx_revision_resource ON resource_revisions(resource_id);
