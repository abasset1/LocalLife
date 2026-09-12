-- LL-11010 (Sprint 11, section 13) : modèle ExternalActivity — voir
-- docs/05_Sprints/SPRINT_11.md et la javadoc de ExternalActivity.java.
-- raw_payload en JSONB : réutilise les convertisseurs Spring Data JDBC
-- déjà enregistrés pour ActivityMetadata (LL-11009,
-- StringToJsonbConverter/JsonbToStringConverter, voir
-- docs/02_Architecture/ADR-0004-metadata-jsonb.md) — aucun nouveau
-- convertisseur nécessaire, ils s'appliquent à toute colonne jsonb.

CREATE TABLE external_activity (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES source(id),
    external_id VARCHAR(255) NOT NULL,
    external_url VARCHAR(1024),
    source_updated_at TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    raw_payload JSONB NOT NULL,
    -- Même principe que idx_activity_source_import_key (V9/LL-5007) : un même élément source,
    -- recollecté plusieurs fois, met à jour la même ligne plutôt que d'en créer une nouvelle.
    CONSTRAINT uq_external_activity_source_id_external_id UNIQUE (source_id, external_id)
);

CREATE INDEX idx_external_activity_source_id ON external_activity (source_id);
