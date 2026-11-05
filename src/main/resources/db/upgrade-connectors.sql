CREATE TABLE IF NOT EXISTS source_connections (
  id VARCHAR(255) PRIMARY KEY, user_id VARCHAR(255) NOT NULL, name VARCHAR(200) NOT NULL,
  provider VARCHAR(30) NOT NULL, origin VARCHAR(300) NOT NULL, encrypted_url VARCHAR(6000) NOT NULL,
  encrypted_auth VARCHAR(6000) NOT NULL, schema_id VARCHAR(255), key_path VARCHAR(1000), name_path VARCHAR(1000),
  mapping_json JSON, links_json JSON, auto_sync_minutes INTEGER, last_sync_at TIMESTAMP WITH TIME ZONE, last_status VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_source_owner ON source_connections(user_id);
