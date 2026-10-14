-- ADR-0011, § 7.2.9 (décision d'Alex du 14/10/2026) : lien RÉVERSIBLE d'un lieu d'événement vers le lieu permanent
-- qu'il désigne. Aucune fusion, aucune réaffectation de programmation : le lieu d'événement garde son nom, son
-- adresse et sa position ; les événements s'affichent comme avant. Délier = remettre place_id à NULL.
--
-- Liaison posée uniquement par un administrateur (paires du rapport de rapprochement), jamais automatiquement.
-- Aucune donnée existante n'est modifiée par cette migration.

ALTER TABLE location
    ADD COLUMN place_id BIGINT REFERENCES location (id) ON DELETE SET NULL,
    ADD COLUMN place_linked_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN place_linked_at TIMESTAMPTZ;

-- Seul un lieu d'événement (sans statut de lieu permanent) se relie, et jamais à lui-même.
ALTER TABLE location ADD CONSTRAINT chk_location_place_link CHECK (
    place_id IS NULL OR (place_status IS NULL AND place_id <> id));

CREATE INDEX idx_location_place_id ON location (place_id) WHERE place_id IS NOT NULL;
