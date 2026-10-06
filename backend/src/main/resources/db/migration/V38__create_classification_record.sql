-- CAT-008 (SPRINT_CATEG) : statuts de classification et file de relecture.
--
-- activity.primary_category_id est NOT NULL (V37) : une activité sans catégorie
-- ne peut pas exister, et ce ticket ne contourne pas cette contrainte. Un
-- élément importé dont la classification est ambiguë (NEEDS_REVIEW) ou sans
-- signal (UNCLASSIFIED) n'est donc PAS créé comme activité : il est consigné
-- ici, avec la raison et les catégories candidates, pour relecture. Aucune
-- catégorie n'est inventée pour le faire entrer dans `activity`.
--
-- Une ligne par élément de source (source_id + import_key), mise à jour à
-- chaque ré-import tant que l'élément n'est pas classé.
--   * AUTO_CLASSIFIED / VALIDATED : activity_id renseigné (l'activité existe)
--   * UNCLASSIFIED / NEEDS_REVIEW / REJECTED : activity_id NULL (en attente ou écarté)
-- Cette règle est imposée par la contrainte ck_classification_record_activity.
CREATE TABLE classification_record (
    id                       BIGSERIAL PRIMARY KEY,
    source_id                BIGINT       NOT NULL REFERENCES source(id),
    import_key               VARCHAR(255),
    activity_id              BIGINT       UNIQUE REFERENCES activity(id) ON DELETE CASCADE,
    status                   VARCHAR(30)  NOT NULL,
    rule_id                  VARCHAR(100) NOT NULL,
    reason                   VARCHAR(100),
    candidate_category_codes VARCHAR(255),
    source_category          VARCHAR(255),
    source_tags              VARCHAR(1000),
    title                    VARCHAR(255),
    created_at               TIMESTAMP    NOT NULL,
    updated_at               TIMESTAMP    NOT NULL,
    CONSTRAINT ck_classification_record_status
        CHECK (status IN ('UNCLASSIFIED', 'AUTO_CLASSIFIED', 'VALIDATED', 'NEEDS_REVIEW', 'REJECTED')),
    CONSTRAINT ck_classification_record_activity
        CHECK ((activity_id IS NOT NULL) = (status IN ('AUTO_CLASSIFIED', 'VALIDATED')))
);

-- Même principe que idx_activity_source_import_key (V9) : unicité par source,
-- les contributions manuelles (import_key NULL) n'étant pas concernées.
CREATE UNIQUE INDEX idx_classification_record_source_key
    ON classification_record (source_id, import_key) WHERE import_key IS NOT NULL;
CREATE INDEX idx_classification_record_status ON classification_record (status);
