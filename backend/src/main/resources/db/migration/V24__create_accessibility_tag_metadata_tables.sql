-- LL-11009 (Sprint 11, section 12) : accessibilité (modèle structuré),
-- tags (plusieurs par activité), et metadata (JSONB, données
-- spécifiques/évolutives) — voir docs/05_Sprints/SPRINT_11.md et les
-- javadoc de Accessibility.java/ActivityTag.java/ActivityMetadata.java.
-- Trois tables séparées, chacune un-à-un ou un-à-plusieurs avec
-- activity, aucune ne modifie la table activity elle-même.

-- Accessibilité : catégories reconnues au niveau national/international
-- (moteur, auditif, visuel, psychique, intellectuel — voir la javadoc
-- de Accessibility.java) ; nullable chacun (absence = non renseigné,
-- distinct de "non accessible").
CREATE TABLE accessibility (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL UNIQUE REFERENCES activity(id),
    motor_impairment BOOLEAN,
    hearing_impairment BOOLEAN,
    visual_impairment BOOLEAN,
    psychic_impairment BOOLEAN,
    intellectual_impairment BOOLEAN
);

-- Tags : plusieurs par activité (critère d'acceptation explicite).
CREATE TABLE activity_tag (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity(id),
    tag VARCHAR(100) NOT NULL,
    CONSTRAINT uq_activity_tag_activity_id_tag UNIQUE (activity_id, tag)
);
CREATE INDEX idx_activity_tag_activity_id ON activity_tag (activity_id);

-- Metadata : JSONB pour tout le reste (critère d'acceptation « données
-- spécifiques conservables » / « aucune table créée inutilement pour
-- chaque champ d'une source »). Un seul enregistrement par activité.
CREATE TABLE activity_metadata (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL UNIQUE REFERENCES activity(id),
    data JSONB NOT NULL
);
