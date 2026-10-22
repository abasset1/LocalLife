-- RGPD : consentement à l'inscription, durées de conservation, effacement des journaux (décisions du 22/10/2026,
-- docs/02_Architecture/RGPD.md).

-- 1. Consentement à l'inscription : « J'ai au moins 15 ans et j'accepte les conditions d'utilisation et la politique
--    de confidentialité », enregistré avec sa date et la version des textes acceptés. NULL pour les comptes
--    antérieurs (aucun consentement n'a été recueilli).
ALTER TABLE users
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_version VARCHAR(20),
    -- Dernière activité (connexion ou requête authentifiée, au jour près) : base de la suppression des comptes
    -- inactifs (avertissement après 3 ans, suppression 30 jours plus tard).
    ADD COLUMN last_active_at TIMESTAMPTZ,
    ADD COLUMN inactivity_warned_at TIMESTAMPTZ;

-- L'inactivité des comptes existants se compte à partir d'aujourd'hui.
UPDATE users SET last_active_at = now();

-- 2. Journaux en ajout seul : seules deux opérations restent possibles, et seulement depuis une transaction qui
--    l'annonce (SET LOCAL locallife.retention = 'on', posé par RetentionService et l'effacement d'un compte) :
--    supprimer une ligne de plus de 3 ans (durée de conservation) et effacer l'adresse e-mail d'un compte supprimé.
--    Toute autre modification reste refusée.
CREATE OR REPLACE FUNCTION account_audit_event_is_append_only() RETURNS trigger AS $$
BEGIN
    IF current_setting('locallife.retention', true) = 'on' THEN
        IF TG_OP = 'DELETE' AND OLD.occurred_at < now() - interval '3 years' THEN
            RETURN OLD;
        END IF;
        IF TG_OP = 'UPDATE'
                AND NEW.id = OLD.id AND NEW.occurred_at = OLD.occurred_at
                AND NEW.actor_user_id IS NOT DISTINCT FROM OLD.actor_user_id
                AND NEW.target_user_id IS NOT DISTINCT FROM OLD.target_user_id
                AND NEW.action = OLD.action AND NEW.outcome = OLD.outcome
                AND NEW.reason IS NOT DISTINCT FROM OLD.reason AND NEW.detail IS NOT DISTINCT FROM OLD.detail
                AND (NEW.actor_email IS NULL OR NEW.actor_email = OLD.actor_email)
                AND (NEW.old_value IS NULL OR NEW.old_value = OLD.old_value)
                AND (NEW.new_value IS NULL OR NEW.new_value = OLD.new_value) THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'account_audit_event est un journal en ajout seul (% interdit)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION admin_action_log_append_only() RETURNS trigger AS $$
BEGIN
    IF current_setting('locallife.retention', true) = 'on' THEN
        IF TG_OP = 'DELETE' AND OLD.occurred_at < now() - interval '3 years' THEN
            RETURN OLD;
        END IF;
        IF TG_OP = 'UPDATE'
                AND NEW.id = OLD.id AND NEW.occurred_at = OLD.occurred_at
                AND NEW.actor_id IS NOT DISTINCT FROM OLD.actor_id
                AND NEW.action = OLD.action AND NEW.target_type = OLD.target_type
                AND NEW.target_id IS NOT DISTINCT FROM OLD.target_id
                AND NEW.summary IS NOT DISTINCT FROM OLD.summary
                AND NEW.before_value IS NOT DISTINCT FROM OLD.before_value
                AND NEW.after_value IS NOT DISTINCT FROM OLD.after_value
                AND NEW.reason IS NOT DISTINCT FROM OLD.reason
                AND NEW.actor_email IS NULL THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'admin_action_log est en ajout seul';
END;
$$ LANGUAGE plpgsql;

CREATE INDEX idx_users_inactivity ON users (last_active_at) WHERE role <> 'ADMIN';
CREATE INDEX idx_users_unverified ON users (created_at) WHERE email_verified = FALSE;
