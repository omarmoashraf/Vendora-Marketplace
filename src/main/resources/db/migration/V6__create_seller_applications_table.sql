CREATE TABLE seller_applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    business_name VARCHAR(255) NOT NULL,
    notes TEXT,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by_admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_seller_applications_user_id ON seller_applications(user_id);
CREATE UNIQUE INDEX idx_seller_applications_user_pending ON seller_applications(user_id) WHERE status = 'PENDING';
