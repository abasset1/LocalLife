-- Sprint « Rôles, permissions et gestion des comptes » (ACC-02, AUD-01).
--
-- 1. Statut de compte, distinct du rôle (users.role) et de l'offre (subscription, V67). Valeur par défaut
--    ACTIVE : tous les comptes existants restent utilisables. La colonne n'est volontairement pas portée par
--    l'entité User (Spring Data JDBC n'écrit que les colonnes mappées) : aucune sauvegarde d'un User (profil,
--    mot de passe, confirmation d'e-mail, Google) ne peut réactiver un compte suspendu par mégarde. Seul
--    AccountStatusRepository la modifie.
ALTER TABLE users
    ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN status_reason VARCHAR(500),
    ADD COLUMN status_changed_at TIMESTAMP,
    ADD COLUMN status_changed_by BIGINT,
    ADD CONSTRAINT chk_users_account_status CHECK (account_status IN ('ACTIVE', 'SUSPENDED'));

CREATE INDEX idx_users_account_status ON users (account_status);
CREATE INDEX idx_users_role ON users (role);

-- 2. Journal d'audit des actions sensibles sur les comptes (changements de rôle, suspension, réactivation),
--    réussies ou refusées. Pas de clé étrangère : une entrée reste lisible telle qu'elle a été écrite, y compris
--    pour une cible inexistante (tentative refusée) ; l'adresse de l'acteur est figée au moment de l'action.
--    Aucun mot de passe, JWT ni jeton n'y est écrit (valeurs : rôles et statuts uniquement).
CREATE TABLE account_audit_event (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    actor_user_id BIGINT,
    actor_email VARCHAR(255),
    target_user_id BIGINT,
    action VARCHAR(30) NOT NULL,
    old_value VARCHAR(50),
    new_value VARCHAR(50),
    reason VARCHAR(500),
    outcome VARCHAR(20) NOT NULL,
    detail VARCHAR(500),
    CONSTRAINT chk_account_audit_action CHECK (action IN ('ROLE_CHANGE', 'SUSPEND', 'REACTIVATE')),
    CONSTRAINT chk_account_audit_outcome CHECK (outcome IN ('SUCCESS', 'REFUSED'))
);

CREATE INDEX idx_account_audit_target ON account_audit_event (target_user_id, occurred_at DESC);
CREATE INDEX idx_account_audit_occurred ON account_audit_event (occurred_at DESC);

-- Journal en ajout seul : ni modification ni suppression, même par une requête SQL de l'application.
CREATE FUNCTION account_audit_event_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'account_audit_event est un journal en ajout seul (% interdit)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER account_audit_event_no_update_or_delete
    BEFORE UPDATE OR DELETE ON account_audit_event
    FOR EACH ROW EXECUTE FUNCTION account_audit_event_is_append_only();
