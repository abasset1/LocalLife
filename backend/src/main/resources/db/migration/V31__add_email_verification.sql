-- Vérification de l'adresse email à l'inscription.
--
-- users.email_verified : FALSE pour tout nouveau compte créé par
-- inscription classique tant que le lien envoyé par email n'a pas été
-- suivi. Les comptes déjà présents au moment de cette migration sont
-- considérés comme vérifiés (UPDATE ci-dessous) : ils ont été créés avant
-- l'existence du mécanisme, et les bloquer à la connexion sans qu'ils
-- aient jamais reçu de lien de confirmation serait une régression.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET email_verified = TRUE;

-- Même patron que password_reset_token (V16) : token_hash stocke le
-- SHA-256 (hex) du token, jamais le token en clair (voir
-- EmailVerificationService) — une fuite de cette table ne permet pas à
-- elle seule de confirmer une adresse.
CREATE TABLE email_verification_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_token_user_id ON email_verification_token(user_id);
