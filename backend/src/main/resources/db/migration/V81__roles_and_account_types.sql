-- Rôles et types de compte séparés (décision du 02/11/2026).
--
-- * Type de compte = offre : Gratuit (aucun abonnement en cours), Premium ou Pro (table subscription).
-- * Rôle : USER (utilisateur), LOCALLIFE (équipe LocalLife : modération), ADMIN (administrateur).
--
-- L'ancien rôle PRO disparaît :
-- 1) un rôle PRO attribué par un administrateur (pro_role_source ADMIN, ou inconnu) sans offre Pro en cours devient
--    un compte Pro sans échéance, offert par LocalLife (provider MANUAL) : le compte garde son espace professionnel.
--    Un éventuel abonnement Premium en cours est remplacé ;
-- 2) un rôle PRO qui découlait d'une offre Pro (pro_role_source SUBSCRIPTION) n'a rien à créer : l'abonnement existe ;
-- 3) tous ces comptes repassent au rôle USER, et l'origine du rôle PRO (pro_role_source, V71) est supprimée.

CREATE TEMPORARY TABLE v81_pro_by_admin ON COMMIT DROP AS
SELECT u.id AS user_id
FROM users u
WHERE u.role = 'PRO'
  AND COALESCE(u.pro_role_source, 'ADMIN') = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM subscription s
      WHERE s.user_id = u.id AND s.plan = 'PRO' AND s.status IN ('ACTIVE', 'CANCELED')
        AND (s.current_period_end IS NULL OR s.current_period_end > now()));

-- Abonnement en cours d'une autre offre (Premium, ou Pro échu non encore expiré) : remplacé.
CREATE TEMPORARY TABLE v81_replaced ON COMMIT DROP AS
SELECT s.id, s.status AS from_status
FROM subscription s JOIN v81_pro_by_admin p ON p.user_id = s.user_id
WHERE s.status IN ('ACTIVE', 'CANCELED');

UPDATE subscription s SET status = 'REPLACED', current_period_end = now(), updated_at = now()
FROM v81_replaced r WHERE s.id = r.id;

INSERT INTO subscription_event (subscription_id, actor_id, from_status, to_status, reason)
SELECT id, NULL, from_status, 'REPLACED', 'Rôle PRO converti en compte Pro (V81)' FROM v81_replaced;

WITH created AS (
    INSERT INTO subscription (user_id, plan, status, current_period_start, current_period_end, provider,
                              decision_reason)
    SELECT user_id, 'PRO', 'ACTIVE', now(), NULL, 'MANUAL',
           'Ancien rôle PRO attribué par un administrateur, converti en compte Pro sans échéance (V81)'
    FROM v81_pro_by_admin
    RETURNING id
)
INSERT INTO subscription_event (subscription_id, actor_id, from_status, to_status, reason)
SELECT id, NULL, NULL, 'ACTIVE', 'Ancien rôle PRO converti en compte Pro sans échéance (V81)' FROM created;

UPDATE users SET role = 'USER' WHERE role = 'PRO';

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_pro_role_source;
ALTER TABLE users DROP COLUMN pro_role_source;

ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'LOCALLIFE', 'ADMIN'));
