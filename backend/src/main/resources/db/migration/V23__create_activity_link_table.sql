-- LL-11008 (Sprint 11, section 11) : modèle ActivityLink — voir
-- docs/05_Sprints/SPRINT_11.md et la javadoc de ActivityLink.java.
-- Pas de table de liaison séparée (contrairement à media/activity_media,
-- V22) : un lien n'appartient qu'à une seule activité.
-- type/value NOT NULL (un lien sans l'un ou l'autre n'a pas de sens) ;
-- label nullable (voir la javadoc du domaine).

CREATE TABLE activity_link (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity(id),
    type VARCHAR(50) NOT NULL,
    label VARCHAR(255),
    value VARCHAR(1024) NOT NULL
);

-- Sert le critère d'acceptation « plusieurs liens possibles » (une activité a souvent
-- plusieurs liens : site + billetterie + réseau social...).
CREATE INDEX idx_activity_link_activity_id ON activity_link (activity_id);
