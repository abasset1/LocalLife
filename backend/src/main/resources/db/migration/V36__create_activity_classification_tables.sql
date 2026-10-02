-- CAT-006 (SPRINT_CATEG) : fait évoluer Activity vers le nouveau
-- référentiel (primaryCategory, secondaryCategories[], activityTypes[],
-- audiences[], organizerType), sans toucher à la table `activity` ni à
-- aucune colonne existante — même principe que `accessibility` et
-- `activity_metadata` (V24, LL-11009) : une relation nouvelle = une
-- table séparée, jamais une colonne ajoutée à `activity`.
--
-- Pourquoi une table séparée pour primaryCategory/organizerType plutôt
-- qu'une colonne `activity.primary_category_id` : le critère du ticket
-- impose que la catégorie principale soit obligatoire, mais les ~47 000
-- activités déjà en base (audit CAT-000) n'ont aucune classification —
-- une colonne NOT NULL échouerait immédiatement, et une colonne nullable
-- laisserait croire que "obligatoire" n'est qu'une suggestion. Une table
-- séparée rend le choix explicite : une activité SANS ligne dans
-- activity_primary_category est une activité "pas encore classifiée",
-- un état réel et temporaire (CAT-007, migration des données
-- existantes), pas une violation silencieuse d'une contrainte NOT NULL.
-- "Obligatoire" est donc appliqué au niveau service
-- (ActivityClassificationService), pas encore au niveau DB.

-- 1 catégorie principale (0..1 en base tant que CAT-007 n'a pas migré les
-- données existantes ; le service applicatif, lui, la traite comme
-- obligatoire pour toute nouvelle classification).
CREATE TABLE activity_primary_category (
    id          BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL UNIQUE REFERENCES activity(id),
    category_id BIGINT NOT NULL REFERENCES category(id)
);

-- 0..N catégories secondaires. UNIQUE(activity_id, category_id) :
-- empêche une même catégorie secondaire en double sur une activité
-- (critère "relations cohérentes et sans doublons"). Rien n'empêche en
-- base qu'une catégorie secondaire soit identique à la catégorie
-- principale de la même activité (deux tables séparées, pas de contrainte
-- croisée possible côté SQL) : ce cas est rejeté côté service
-- (ActivityClassificationService), voir sa javadoc.
CREATE TABLE activity_secondary_category (
    id          BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity(id),
    category_id BIGINT NOT NULL REFERENCES category(id),
    CONSTRAINT uq_activity_secondary_category UNIQUE (activity_id, category_id)
);
CREATE INDEX idx_activity_secondary_category_activity_id ON activity_secondary_category (activity_id);

-- 0..N types d'activité.
CREATE TABLE activity_activity_type (
    id               BIGSERIAL PRIMARY KEY,
    activity_id      BIGINT NOT NULL REFERENCES activity(id),
    activity_type_id BIGINT NOT NULL REFERENCES activity_type(id),
    CONSTRAINT uq_activity_activity_type UNIQUE (activity_id, activity_type_id)
);
CREATE INDEX idx_activity_activity_type_activity_id ON activity_activity_type (activity_id);

-- 0..N publics.
CREATE TABLE activity_audience (
    id          BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity(id),
    audience_id BIGINT NOT NULL REFERENCES audience(id),
    CONSTRAINT uq_activity_audience UNIQUE (activity_id, audience_id)
);
CREATE INDEX idx_activity_audience_activity_id ON activity_audience (activity_id);

-- 0..1 type d'organisateur : nullable par nature (le ticket l'exprime
-- comme "0..1"), une table séparée avec activity_id UNIQUE joue le même
-- rôle qu'une colonne nullable, sans modifier `activity`.
CREATE TABLE activity_organizer_type (
    id               BIGSERIAL PRIMARY KEY,
    activity_id      BIGINT NOT NULL UNIQUE REFERENCES activity(id),
    organizer_type_id BIGINT NOT NULL REFERENCES organizer_type(id)
);
