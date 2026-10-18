-- ADR-0012 (PRO-01, décisions du 18/10/2026) : espace professionnel, lots A (revendication d'un lieu existant) et
-- B (création d'un lieu par un professionnel). Le lot C (food truck) attend ARCHI-01.
--
-- Rôle PRO : users.role est un VARCHAR sans contrainte, la valeur PRO n'exige aucune migration de colonne.
--
-- Rien ne change pour les lieux existants : identifiants, provenance (location_source), statuts et affichage restent
-- tels quels. Une revendication n'écrit jamais dans location ; seule son approbation ouvre un droit de gestion
-- (place_manager).

-- Lieu créé par un professionnel : brouillon (DRAFT) avant soumission à la modération existante (PENDING).
ALTER TABLE location DROP CONSTRAINT chk_location_permanent_place;
ALTER TABLE location ADD CONSTRAINT chk_location_permanent_place CHECK (
    (place_type IS NULL AND place_status IS NULL)
    OR (place_type IS NOT NULL AND place_status IN ('DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED')));

ALTER TABLE location
    ADD COLUMN created_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- Lieu existant que le professionnel a signalé comme proche du sien, à relire par le modérateur.
    ADD COLUMN reported_similar_to BIGINT REFERENCES location (id) ON DELETE SET NULL;

-- Demande d'administration d'un lieu existant. Les champs relationship/evidence sont effacés 12 mois après la
-- décision (evidence_purged_at), la demande et son historique restent.
CREATE TABLE place_claim (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    -- Nul si le compte du demandeur est supprimé : la demande et son historique restent pour l'audit.
    requester_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'INFO_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    relationship TEXT,
    evidence TEXT,
    decision_reason TEXT,
    moderator_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    evidence_purged_at TIMESTAMPTZ
);

-- Une seule demande active par professionnel et par lieu.
CREATE UNIQUE INDEX uq_place_claim_active ON place_claim (location_id, requester_id)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'INFO_REQUESTED');
CREATE INDEX idx_place_claim_requester ON place_claim (requester_id);
CREATE INDEX idx_place_claim_queue ON place_claim (status, submitted_at);

-- Trace d'audit : une ligne par transition, jamais modifiée.
CREATE TABLE place_claim_event (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES place_claim (id) ON DELETE CASCADE,
    actor_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_place_claim_event_claim ON place_claim_event (claim_id, id);

-- Droit de gérer un lieu : ouvert par l'approbation d'une revendication (claim_id) ou par la création du lieu
-- (claim_id nul). Plusieurs gestionnaires possibles (décision D3). Une révocation garde la ligne (revoked_at).
CREATE TABLE place_manager (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    claim_id BIGINT REFERENCES place_claim (id) ON DELETE SET NULL,
    granted_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_place_manager_active ON place_manager (location_id, user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_place_manager_user ON place_manager (user_id) WHERE revoked_at IS NULL;
