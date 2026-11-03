-- Connexions de chaque compte, affichées dans Administration → Comptes : nombre de connexions réussies (mot de passe
-- ou Google, chaque jeton délivré à la connexion) et date de la dernière. Comptées à partir de cette migration : les
-- connexions antérieures ne sont pas connues (0 et date vide).
ALTER TABLE users
    ADD COLUMN login_count   INTEGER   NOT NULL DEFAULT 0,
    ADD COLUMN last_login_at TIMESTAMP;
