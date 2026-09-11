-- LL-11007 (Sprint 11, section 10) : modèle Media, indépendant de
-- Activity — voir docs/05_Sprints/SPRINT_11.md et la javadoc de
-- Media.java/ActivityMedia.java. Aucune colonne de activity n'est
-- modifiée (même principe que les migrations précédentes de ce sprint).

CREATE TABLE media (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(1024) NOT NULL,
    type VARCHAR(50),
    width INTEGER,
    height INTEGER,
    credit VARCHAR(255),
    alt_text VARCHAR(255)
);

CREATE TABLE activity_media (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity(id),
    media_id BIGINT NOT NULL REFERENCES media(id),
    position INTEGER NOT NULL,
    -- Empêche d'attacher deux fois le même média à la même activité (pas de contrainte
    -- équivalente sur position : deux médias pourraient légitimement partager une position
    -- transitoirement lors d'une réorganisation, à charge de l'appelant de les distinguer).
    CONSTRAINT uq_activity_media_activity_id_media_id UNIQUE (activity_id, media_id)
);

-- Sert directement le critère d'acceptation « ordre conservé »
-- (ActivityMediaRepository#findByActivityIdOrderByPosition).
CREATE INDEX idx_activity_media_activity_id ON activity_media (activity_id, position);
