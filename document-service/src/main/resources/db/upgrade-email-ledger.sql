-- Additive upgrade for an existing H2 database; leaves all business data untouched.
CREATE TABLE IF NOT EXISTS email_deliveries (
    id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
