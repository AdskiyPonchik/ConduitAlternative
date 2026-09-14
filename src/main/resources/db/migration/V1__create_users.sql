CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       username VARCHAR(100) NOT NULL,
                       email VARCHAR(254) NOT NULL,
                       password_hash VARCHAR(500) NOT NULL,
                       bio VARCHAR(300) NOT NULL DEFAULT '',
                       image_url VARCHAR(2048) NOT NULL DEFAULT '',
                       role VARCHAR(16) NOT NULL DEFAULT 'USER',
                       created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                       updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                       version BIGINT NOT NULL DEFAULT 0,

                       CONSTRAINT ck_users_username_not_blank
                           CHECK (btrim(username) <> ''),

                       CONSTRAINT ck_users_email_not_blank
                           CHECK (btrim(email) <> ''),

                       CONSTRAINT ck_users_password_hash_not_blank
                           CHECK (btrim(password_hash) <> ''),

                       CONSTRAINT ck_users_role
                           CHECK (role IN ('USER', 'MODERATOR', 'ADMIN'))
);

CREATE UNIQUE INDEX ux_users_username_lower
    ON users (lower(username));

CREATE UNIQUE INDEX ux_users_email_lower
    ON users (lower(email));