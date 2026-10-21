-- Actions d'un utilisateur sur son propre compte (décisions du 21/10/2026, ROLES_ET_PERMISSIONS.md § 3 bis).

-- 1. Jetons émis avant un changement d'identifiants (mot de passe changé ou réinitialisé) : refusés. Le filtre JWT
--    compare la date d'émission du jeton à cette date (UTC, à la seconde). NULL : aucun jeton n'est invalidé.
ALTER TABLE users ADD COLUMN tokens_valid_after TIMESTAMP;

-- 2. Changement d'adresse e-mail confirmé : la nouvelle adresse n'est appliquée qu'au clic sur le lien qui lui est
--    envoyé. Une seule demande en cours par compte ; jeton stocké haché (SHA-256), comme V16 et V31.
CREATE TABLE email_change_request (
    user_id BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    new_email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 3. Suppression de compte : l'historique d'abonnement est conservé sans lien vers la personne (auparavant
--    supprimé en cascade avec le compte).
ALTER TABLE subscription ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE subscription DROP CONSTRAINT subscription_user_id_fkey;
ALTER TABLE subscription ADD CONSTRAINT subscription_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

-- 4. Journal des comptes : actions de l'utilisateur sur son propre compte.
ALTER TABLE account_audit_event DROP CONSTRAINT chk_account_audit_action;
ALTER TABLE account_audit_event ADD CONSTRAINT chk_account_audit_action CHECK (action IN (
    'ROLE_CHANGE', 'SUSPEND', 'REACTIVATE', 'PASSWORD_CHANGE', 'EMAIL_CHANGE_REQUEST', 'EMAIL_CHANGE',
    'ACCOUNT_DELETION'));
