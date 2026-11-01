-- Suivre une activité ou un lieu (cloche « Suivre ») : être prévenu de ses mises à jour. Réservé aux offres
-- Premium et Pro (vérifié par l'application, PlanLimits).
--
-- 1) follow : une ligne par utilisateur et par activité OU lieu permanent suivi (exactement l'un des deux).
--    last_change_id : dernier changement déjà notifié (follow_change.id) ; à la création, le dernier
--    changement existant, pour ne jamais notifier ce qui s'est passé avant.
--    notified_until : instant jusqu'auquel les nouvelles activités d'un lieu suivi ont été notifiées.
CREATE TABLE follow (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    activity_id    BIGINT REFERENCES activity (id) ON DELETE CASCADE,
    location_id    BIGINT REFERENCES location (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_change_id BIGINT      NOT NULL DEFAULT 0,
    notified_until TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_follow_target CHECK ((activity_id IS NULL) <> (location_id IS NULL))
);

CREATE UNIQUE INDEX uq_follow_activity ON follow (user_id, activity_id) WHERE activity_id IS NOT NULL;
CREATE UNIQUE INDEX uq_follow_location ON follow (user_id, location_id) WHERE location_id IS NOT NULL;
CREATE INDEX idx_follow_activity ON follow (activity_id) WHERE activity_id IS NOT NULL;
CREATE INDEX idx_follow_location ON follow (location_id) WHERE location_id IS NOT NULL;

-- 2) follow_change : changements notables d'une activité ou d'un lieu suivi, écrits par les triggers
--    ci-dessous quel que soit le chemin (import, contributeur, administration, espace professionnel).
--    Rien n'est écrit pour une activité ou un lieu que personne ne suit. kinds : nature des changements
--    (DATES, LOCATION, DETAILS, CANCELLED, DELETED, CONTACT, CLOSED). Purgés après 30 jours par l'application.
CREATE TABLE follow_change (
    id          BIGSERIAL PRIMARY KEY,
    activity_id BIGINT REFERENCES activity (id) ON DELETE CASCADE,
    location_id BIGINT REFERENCES location (id) ON DELETE CASCADE,
    kinds       TEXT[]      NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_follow_change_target CHECK ((activity_id IS NULL) <> (location_id IS NULL))
);

CREATE INDEX idx_follow_change_activity ON follow_change (activity_id, id) WHERE activity_id IS NOT NULL;
CREATE INDEX idx_follow_change_location ON follow_change (location_id, id) WHERE location_id IS NOT NULL;
CREATE INDEX idx_follow_change_changed_at ON follow_change (changed_at);

-- 3) Activité : changements visibles du public seulement.
--    * Annulation (→ CANCELLED) et suppression par un administrateur (→ DELETED).
--    * Dates, lieu (adresse, ville, coordonnées) ou contenu (titre, descriptions, conditions, âges, lien) modifiés
--      sur une activité publiée ou annulée.
--    * Retour en publication d'une activité déjà publiée auparavant (modification d'un contributeur relue par la
--      modération, annulation levée) : « mise à jour », car le détail a changé pendant que l'activité était masquée.
--    Une activité qui repasse en attente de relecture, est refusée ou se termine (ARCHIVED) ne notifie rien.
CREATE OR REPLACE FUNCTION activity_record_follow_change()
RETURNS TRIGGER AS $$
DECLARE
    changes TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF NOT EXISTS (SELECT 1 FROM follow WHERE activity_id = NEW.id) THEN
        RETURN NEW;
    END IF;

    IF NEW.status = 'CANCELLED' AND OLD.status IS DISTINCT FROM 'CANCELLED' THEN
        changes := array_append(changes, 'CANCELLED');
    ELSIF NEW.status = 'DELETED' AND OLD.status IS DISTINCT FROM 'DELETED' THEN
        changes := array_append(changes, 'DELETED');
    ELSIF NEW.status = 'PUBLISHED' AND OLD.status IN ('PENDING', 'CANCELLED', 'REJECTED')
            AND OLD.published_at IS NOT NULL THEN
        changes := array_append(changes, 'DETAILS');
    ELSIF NEW.status IN ('PUBLISHED', 'CANCELLED') AND OLD.status = NEW.status THEN
        IF NEW.start_date IS DISTINCT FROM OLD.start_date OR NEW.end_date IS DISTINCT FROM OLD.end_date THEN
            changes := array_append(changes, 'DATES');
        END IF;
        IF NEW.address IS DISTINCT FROM OLD.address OR NEW.city IS DISTINCT FROM OLD.city
                OR NEW.postal_code IS DISTINCT FROM OLD.postal_code
                OR NEW.latitude IS DISTINCT FROM OLD.latitude OR NEW.longitude IS DISTINCT FROM OLD.longitude THEN
            changes := array_append(changes, 'LOCATION');
        END IF;
        IF NEW.title IS DISTINCT FROM OLD.title OR NEW.description IS DISTINCT FROM OLD.description
                OR NEW.long_description IS DISTINCT FROM OLD.long_description
                OR NEW.conditions IS DISTINCT FROM OLD.conditions
                OR NEW.age_min IS DISTINCT FROM OLD.age_min OR NEW.age_max IS DISTINCT FROM OLD.age_max
                OR NEW.url IS DISTINCT FROM OLD.url THEN
            changes := array_append(changes, 'DETAILS');
        END IF;
    END IF;

    IF cardinality(changes) > 0 THEN
        INSERT INTO follow_change (activity_id, kinds) VALUES (NEW.id, changes);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_activity_record_follow_change
    AFTER UPDATE ON activity
    FOR EACH ROW
    EXECUTE FUNCTION activity_record_follow_change();

-- 4) Lieu permanent publié : adresse ou coordonnées (LOCATION), nom ou description (DETAILS), contacts (CONTACT)
--    modifiés, ou lieu retiré de la carte (CLOSED : archivé, refusé ou remis en brouillon).
CREATE OR REPLACE FUNCTION location_record_follow_change()
RETURNS TRIGGER AS $$
DECLARE
    changes TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF OLD.place_status IS DISTINCT FROM 'PUBLISHED'
            OR NOT EXISTS (SELECT 1 FROM follow WHERE location_id = NEW.id) THEN
        RETURN NEW;
    END IF;

    IF NEW.place_status IS DISTINCT FROM 'PUBLISHED' THEN
        changes := array_append(changes, 'CLOSED');
    ELSE
        IF NEW.address IS DISTINCT FROM OLD.address OR NEW.city IS DISTINCT FROM OLD.city
                OR NEW.postal_code IS DISTINCT FROM OLD.postal_code
                OR NEW.latitude IS DISTINCT FROM OLD.latitude OR NEW.longitude IS DISTINCT FROM OLD.longitude THEN
            changes := array_append(changes, 'LOCATION');
        END IF;
        IF NEW.name IS DISTINCT FROM OLD.name OR NEW.description IS DISTINCT FROM OLD.description THEN
            changes := array_append(changes, 'DETAILS');
        END IF;
        IF NEW.website IS DISTINCT FROM OLD.website OR NEW.email IS DISTINCT FROM OLD.email
                OR NEW.phone IS DISTINCT FROM OLD.phone THEN
            changes := array_append(changes, 'CONTACT');
        END IF;
    END IF;

    IF cardinality(changes) > 0 THEN
        INSERT INTO follow_change (location_id, kinds) VALUES (NEW.id, changes);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_location_record_follow_change
    AFTER UPDATE ON location
    FOR EACH ROW
    EXECUTE FUNCTION location_record_follow_change();
