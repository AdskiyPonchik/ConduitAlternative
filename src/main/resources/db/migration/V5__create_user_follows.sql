CREATE TABLE user_follows (
                              follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              followed_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              PRIMARY KEY (follower_id, followed_id),
                              CONSTRAINT chk_user_follows_not_self CHECK (follower_id <> followed_id)
);

CREATE INDEX ix_user_follows_followed_id ON user_follows(followed_id);