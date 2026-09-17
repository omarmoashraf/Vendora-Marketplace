CREATE TABLE seller_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    display_name VARCHAR(255) NOT NULL,
    payout_method VARCHAR(50),
    payout_details_json TEXT,
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_seller_profiles_user_id ON seller_profiles(user_id);
