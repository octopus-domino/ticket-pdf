CREATE TABLE IF NOT EXISTS api_client_keys (
  id VARCHAR(255) PRIMARY KEY, user_id VARCHAR(255) NOT NULL, name VARCHAR(100) NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE, prefix VARCHAR(20) NOT NULL,
  encrypted_token VARCHAR(1000) NOT NULL, scopes VARCHAR(100) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE, minute_window BIGINT, minute_count INTEGER,
  month_window VARCHAR(7), month_count INTEGER, last_used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_api_keys_owner ON api_client_keys(user_id);
