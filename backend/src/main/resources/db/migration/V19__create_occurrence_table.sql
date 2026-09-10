-- LL-11004 (Sprint 11) : modèle Occurrence, la matérialisation concrète
-- d'un Schedule — voir docs/05_Sprints/SPRINT_11.md, section 7, et la
-- javadoc de Occurrence.java. schedule_id NOT NULL (une occurrence
-- appartient toujours à un schedule, critère d'acceptation « relation
-- avec Schedule ») ; location_id nullable (lieu effectif, distinct de
-- schedule.location_id, voir la javadoc). start_at/end_at en
-- TIMESTAMPTZ (et non TIMESTAMP comme activity.start_date/end_date) :
-- une occurrence porte un instant déjà résolu (fuseau/DST pris en
-- compte, LL-11003), pas une heure « murale » naïve.
--
-- Aucune colonne de schedule/location n'est modifiée par cette migration
-- (même principe que V17/V18) : Occurrence référence les deux tables
-- sans les altérer.

CREATE TABLE occurrence (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES schedule(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ,
    location_id BIGINT REFERENCES location(id),
    status VARCHAR(50) NOT NULL
);

CREATE INDEX idx_occurrence_schedule_id ON occurrence (schedule_id);
CREATE INDEX idx_occurrence_location_id ON occurrence (location_id);
-- Sert les recherches par date/plage (calendrier, recherche "aujourd'hui"/"ce week-end"),
-- usage prévisible de ce modèle même si aucun ticket de ce sprint n'implémente encore la requête.
CREATE INDEX idx_occurrence_start_at ON occurrence (start_at);
