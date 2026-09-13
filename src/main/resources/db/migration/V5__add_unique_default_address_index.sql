CREATE UNIQUE INDEX idx_addresses_user_default ON addresses (user_id) WHERE is_default = TRUE;
