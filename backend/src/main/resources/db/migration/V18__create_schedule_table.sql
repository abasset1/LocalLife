-- LL-11002 (Sprint 11) : modèle Schedule, la programmation d'une activité
-- — voir docs/05_Sprints/SPRINT_11.md, section 5, et la javadoc de
-- Schedule.java. activity_id NOT NULL (un schedule appartient toujours à
-- une activité) ; location_id nullable (un schedule peut exister sans
-- lieu résolu, voir la javadoc de Schedule) ; tout le reste nullable,
-- même tolérance aux données incomplètes qu'ailleurs dans le projet.
--
-- Aucune colonne de activity/location n'est modifiée par cette migration
-- (critère d'acceptation implicite de compatibilité, même principe que
-- V17) : Schedule référence les deux tables sans les altérer.

CREATE TABLE schedule (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity(id),
    location_id BIGINT REFERENCES location(id),
    start_time TIME,
    end_time TIME,
    valid_from DATE,
    valid_until DATE,
    timezone VARCHAR(64),
    recurrence_rule VARCHAR(255)
);

-- Sert directement le critère d'acceptation « une activité peut avoir
-- plusieurs schedules » (ScheduleRepository#findByActivityId).
CREATE INDEX idx_schedule_activity_id ON schedule (activity_id);
CREATE INDEX idx_schedule_location_id ON schedule (location_id);
