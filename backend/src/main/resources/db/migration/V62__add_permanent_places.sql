-- ADR-0011, phase B (§ 7.2, décisions du 14/10/2026) : lieux permanents, modérés avant publication.
--
-- Un lieu permanent est une ligne de location qui porte un type (place_type) et un statut de modération
-- (place_status). Les lieux d'événement existants n'ont ni l'un ni l'autre : rien ne change pour eux. Le statut
-- d'un lieu ne masque jamais un événement qui s'y déroule.
--
-- place_status : PENDING (importé, en attente), PUBLISHED (validé par un administrateur), REJECTED (refusé,
-- jamais reproposé), ARCHIVED (disparu de la source).

ALTER TABLE location
    ADD COLUMN place_type VARCHAR(50),
    ADD COLUMN place_status VARCHAR(20),
    ADD COLUMN description TEXT,
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN moderated_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN moderated_at TIMESTAMPTZ;

ALTER TABLE location ADD CONSTRAINT chk_location_permanent_place CHECK (
    (place_type IS NULL AND place_status IS NULL)
    OR (place_type IS NOT NULL AND place_status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED')));

CREATE INDEX idx_location_published_place ON location (place_type) WHERE place_status = 'PUBLISHED';
CREATE INDEX idx_location_pending_place ON location (id) WHERE place_status = 'PENDING';

-- Provenance : une ligne par source qui décrit le lieu. Supprimer une source retire sa provenance, pas le lieu.
CREATE TABLE location_source (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL REFERENCES source (id) ON DELETE CASCADE,
    external_id VARCHAR(255) NOT NULL,
    source_types TEXT NOT NULL,
    content_hash VARCHAR(64),
    last_seen_at TIMESTAMPTZ,
    CONSTRAINT uq_location_source_external UNIQUE (source_id, external_id)
);

CREATE INDEX idx_location_source_location ON location_source (location_id);

-- Source des lieux DATAtourisme de PACA (même fichier régional que les événements). Créée INACTIVE : elle ne
-- s'active qu'après un essai à blanc réussi, depuis l'administration.
INSERT INTO source (name, type, url, status, region_filter)
VALUES ('DATAtourisme PACA — lieux (data.gouv.fr)', 'DATATOURISME_LIEUX',
        'https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80', 'INACTIVE', NULL);
