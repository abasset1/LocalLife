-- LL-EF-007 : réinitialisation de mot de passe.
--
-- token_hash stocke le SHA-256 (hex) du token, jamais le token en clair
-- (voir PasswordResetService) : une fuite de cette table ne permet donc
-- pas à elle seule de réinitialiser un mot de passe.
CREATE TABLE password_reset_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_token_user_id ON password_reset_token(user_id);
