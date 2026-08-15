ALTER TABLE activity ADD COLUMN source_id BIGINT;
ALTER TABLE activity ADD COLUMN import_key VARCHAR(255);

-- Rattache toutes les activités existantes (démo + contributions manuelles
-- créées avant ce ticket) à la source réservée MANUAL (LL-5001/V8), pour
-- que source_id puisse devenir NOT NULL sans perte de données.
UPDATE activity SET source_id = (SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1);

ALTER TABLE activity ALTER COLUMN source_id SET NOT NULL;
ALTER TABLE activity ADD CONSTRAINT fk_activity_source FOREIGN KEY (source_id) REFERENCES source(id);

-- Empêche deux activités importées de la même source de partager la même
-- clé de déduplication (LL-5007). Index partiel : n'affecte pas les
-- activités manuelles, dont import_key reste NULL en permanence.
CREATE UNIQUE INDEX idx_activity_source_import_key ON activity (source_id, import_key) WHERE import_key IS NOT NULL;
