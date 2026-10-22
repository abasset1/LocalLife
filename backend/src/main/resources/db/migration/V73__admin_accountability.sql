-- Comptes administrateurs : décisions du 22/10/2026 (ROLES_ET_PERMISSIONS.md § « Administrateur »).

-- 1. Journal unique des actions d'administration : qui, quoi, sur quoi, avant/après, motif, quand. En ajout seul,
--    comme account_audit_event (V69) : la base refuse toute modification ou suppression.
CREATE TABLE admin_action_log (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Identité figée au moment de l'action : le journal reste lisible si le compte disparaît.
    actor_id BIGINT,
    actor_email VARCHAR(255),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(80),
    summary TEXT,
    before_value JSONB,
    after_value JSONB,
    reason TEXT
);

CREATE INDEX idx_admin_action_log_occurred ON admin_action_log (occurred_at DESC, id DESC);
CREATE INDEX idx_admin_action_log_target ON admin_action_log (target_type, target_id, occurred_at DESC);
CREATE INDEX idx_admin_action_log_actor ON admin_action_log (actor_id, occurred_at DESC);

CREATE FUNCTION admin_action_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'admin_action_log est en ajout seul';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admin_action_log_append_only
    BEFORE UPDATE OR DELETE ON admin_action_log
    FOR EACH ROW EXECUTE FUNCTION admin_action_log_append_only();

-- 2. Corbeille : une suppression par un administrateur met l'activité au statut DELETED (invisible partout),
--    restaurable 30 jours, puis purgée. deletion_batch regroupe une suppression en masse pour la restaurer d'un coup.
ALTER TABLE activity DROP CONSTRAINT chk_activity_status;
ALTER TABLE activity ADD CONSTRAINT chk_activity_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED', 'CANCELLED', 'DELETED'));

ALTER TABLE activity
    ADD COLUMN previous_status VARCHAR(50),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN deletion_reason TEXT,
    ADD COLUMN deletion_batch UUID,
    -- Motif d'un refus ou d'une annulation par l'équipe, visible de l'auteur.
    ADD COLUMN moderation_reason TEXT;

CREATE INDEX idx_activity_deleted ON activity (deleted_at) WHERE status = 'DELETED';
CREATE INDEX idx_activity_deletion_batch ON activity (deletion_batch) WHERE deletion_batch IS NOT NULL;

-- 3. Tentatives de connexion échouées : au-delà de 5 en 15 minutes, l'adresse est bloquée jusqu'à la fin de la
--    fenêtre. Adresse stockée hachée (SHA-256), lignes anciennes purgées.
CREATE TABLE login_failure (
    id BIGSERIAL PRIMARY KEY,
    email_hash VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_failure_email ON login_failure (email_hash, occurred_at DESC);

-- 4. Journal des comptes : actions d'administration ajoutées.
ALTER TABLE account_audit_event DROP CONSTRAINT chk_account_audit_action;
ALTER TABLE account_audit_event ADD CONSTRAINT chk_account_audit_action CHECK (action IN (
    'ROLE_CHANGE', 'SUSPEND', 'REACTIVATE', 'PASSWORD_CHANGE', 'EMAIL_CHANGE_REQUEST', 'EMAIL_CHANGE',
    'ACCOUNT_DELETION', 'LOGOUT_EVERYWHERE', 'ADMIN_ACCOUNT_DELETION'));
