-- Comptes professionnels : décisions du 22/10/2026 (ROLES_ET_PERMISSIONS.md § « Professionnel »).

-- 1. L'offre Pro pilote le rôle. Origine du rôle PRO : ADMIN (attribué à la main, indépendant de tout abonnement) ou
--    SUBSCRIPTION (posé à la validation de l'offre Pro, retiré quand elle prend fin). NULL pour les autres rôles.
--    Hors de l'entité User, comme account_status : enregistrer un User ne la modifie jamais.
ALTER TABLE users ADD COLUMN pro_role_source VARCHAR(20)
    CONSTRAINT chk_users_pro_role_source CHECK (pro_role_source IN ('ADMIN', 'SUBSCRIPTION'));
-- Les rôles PRO existants ont tous été attribués par un administrateur.
UPDATE users SET pro_role_source = 'ADMIN' WHERE role = 'PRO';
-- Les abonnés Pro en cours reçoivent le rôle PRO.
UPDATE users u SET role = 'PRO', pro_role_source = 'SUBSCRIPTION'
WHERE u.role = 'USER' AND EXISTS (
    SELECT 1 FROM subscription s
    WHERE s.user_id = u.id AND s.plan = 'PRO' AND s.status IN ('ACTIVE', 'CANCELED')
      AND (s.current_period_end IS NULL OR s.current_period_end > now()));

-- 2. Fin de l'accès professionnel : les food trucks publiés sont suspendus (suspended_for_access_at) et republiés
--    si l'accès revient ; les droits de gestion sont révoqués avec un motif, puis rétablis de même.
ALTER TABLE food_truck ADD COLUMN suspended_for_access_at TIMESTAMPTZ;

ALTER TABLE place_manager
    ADD COLUMN revoked_reason VARCHAR(20)
        CONSTRAINT chk_place_manager_revoked_reason CHECK (revoked_reason IN ('ACCESS_LOST', 'ADMIN', 'RENOUNCED')),
    ADD COLUMN revoked_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN revoke_comment TEXT;
UPDATE place_manager SET revoked_reason = 'ADMIN' WHERE revoked_at IS NOT NULL;

-- 3. Première publication d'un food truck validée par l'équipe : approved_at nul tant qu'elle n'a pas eu lieu.
--    Les food trucks déjà visibles ou suspendus sont réputés validés.
ALTER TABLE food_truck
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN approved_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN review_reason TEXT;
UPDATE food_truck SET approved_at = COALESCE(published_at::timestamptz, now())
WHERE status IN ('PUBLISHED', 'SUSPENDED');

-- 4. Modifications proposées par un gestionnaire sur un lieu publié : appliquées après validation. Une seule
--    proposition en attente par gestionnaire et par lieu.
CREATE TABLE place_change_request (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    requester_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    address VARCHAR(512),
    postal_code VARCHAR(20),
    city VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    description TEXT,
    website VARCHAR(512),
    phone VARCHAR(50),
    email VARCHAR(255),
    decision_reason TEXT,
    moderator_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_place_change_pending ON place_change_request (location_id, requester_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_place_change_queue ON place_change_request (status, created_at);

-- Une modification validée d'un gestionnaire prime sur les réimports : l'import ne réécrit plus ces champs.
ALTER TABLE location ADD COLUMN manager_edited_at TIMESTAMPTZ;

-- 5. Activité proposée par un lieu géré par son auteur : affichée tant que l'auteur gère encore le lieu.
CREATE TABLE activity_host_place (
    activity_id BIGINT PRIMARY KEY REFERENCES activity (id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE
);

CREATE INDEX idx_activity_host_place_location ON activity_host_place (location_id);
