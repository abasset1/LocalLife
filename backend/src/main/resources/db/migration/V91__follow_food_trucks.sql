-- Suivre un food truck (cloche « Suivre », offres Premium et Pro, vérifié par l'application) : être prévenu de ses
-- mises à jour (profil, planning, retrait de la carte) et, le jour où il est quelque part, de l'endroit où le trouver.

-- 1) follow : une ligne porte exactement une cible, activité, lieu ou food truck.
--    presence_notified_on : dernier jour (heure de Paris) où l'abonné a été prévenu de la présence du food truck.
ALTER TABLE follow
    ADD COLUMN food_truck_id BIGINT REFERENCES food_truck (id) ON DELETE CASCADE,
    ADD COLUMN presence_notified_on DATE;

ALTER TABLE follow DROP CONSTRAINT chk_follow_target;
ALTER TABLE follow ADD CONSTRAINT chk_follow_target
    CHECK (num_nonnulls(activity_id, location_id, food_truck_id) = 1);

CREATE UNIQUE INDEX uq_follow_food_truck ON follow (user_id, food_truck_id) WHERE food_truck_id IS NOT NULL;
CREATE INDEX idx_follow_food_truck ON follow (food_truck_id) WHERE food_truck_id IS NOT NULL;

-- 2) follow_change : changements d'un food truck suivi, en plus de ceux des activités et des lieux.
ALTER TABLE follow_change ADD COLUMN food_truck_id BIGINT REFERENCES food_truck (id) ON DELETE CASCADE;

ALTER TABLE follow_change DROP CONSTRAINT chk_follow_change_target;
ALTER TABLE follow_change ADD CONSTRAINT chk_follow_change_target
    CHECK (num_nonnulls(activity_id, location_id, food_truck_id) = 1);

CREATE INDEX idx_follow_change_food_truck ON follow_change (food_truck_id, id) WHERE food_truck_id IS NOT NULL;

-- 3) Food truck publié : retiré de la carte (CLOSED : suspendu, archivé, remis en brouillon…), nom, description ou
--    cuisine (DETAILS), contact ou réseaux sociaux (CONTACT) modifiés.
CREATE OR REPLACE FUNCTION food_truck_record_follow_change()
RETURNS TRIGGER AS $$
DECLARE
    changes TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF OLD.status IS DISTINCT FROM 'PUBLISHED'
            OR NOT EXISTS (SELECT 1 FROM follow WHERE food_truck_id = NEW.id) THEN
        RETURN NEW;
    END IF;

    IF NEW.status IS DISTINCT FROM 'PUBLISHED' THEN
        changes := array_append(changes, 'CLOSED');
    ELSE
        IF NEW.name IS DISTINCT FROM OLD.name OR NEW.description IS DISTINCT FROM OLD.description
                OR NEW.category IS DISTINCT FROM OLD.category
                OR NEW.cuisine_type IS DISTINCT FROM OLD.cuisine_type THEN
            changes := array_append(changes, 'DETAILS');
        END IF;
        IF NEW.contact IS DISTINCT FROM OLD.contact OR NEW.instagram_url IS DISTINCT FROM OLD.instagram_url
                OR NEW.facebook_url IS DISTINCT FROM OLD.facebook_url THEN
            changes := array_append(changes, 'CONTACT');
        END IF;
    END IF;

    IF cardinality(changes) > 0 THEN
        INSERT INTO follow_change (food_truck_id, kinds) VALUES (NEW.id, changes);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_food_truck_record_follow_change
    AFTER UPDATE ON food_truck
    FOR EACH ROW
    EXECUTE FUNCTION food_truck_record_follow_change();

-- 4) Planning d'un food truck publié (PLANNING) : présence publiée ajoutée, modifiée (emplacement, jours, horaires,
--    dates) ou retirée. Un brouillon ne notifie rien.
CREATE OR REPLACE FUNCTION food_truck_presence_record_follow_change()
RETURNS TRIGGER AS $$
DECLARE
    changed BOOLEAN := FALSE;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM follow WHERE food_truck_id = NEW.food_truck_id)
            OR NOT EXISTS (SELECT 1 FROM food_truck WHERE id = NEW.food_truck_id AND status = 'PUBLISHED') THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT' THEN
        changed := NEW.status = 'PUBLISHED';
    ELSIF NEW.status = 'PUBLISHED' OR OLD.status = 'PUBLISHED' THEN
        changed := NEW.status IS DISTINCT FROM OLD.status
            OR NEW.location_id IS DISTINCT FROM OLD.location_id
            OR NEW.days_of_week IS DISTINCT FROM OLD.days_of_week
            OR NEW.start_time IS DISTINCT FROM OLD.start_time OR NEW.end_time IS DISTINCT FROM OLD.end_time
            OR NEW.valid_from IS DISTINCT FROM OLD.valid_from OR NEW.valid_until IS DISTINCT FROM OLD.valid_until;
    END IF;

    IF changed THEN
        INSERT INTO follow_change (food_truck_id, kinds) VALUES (NEW.food_truck_id, ARRAY['PLANNING']);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_food_truck_presence_record_follow_change
    AFTER INSERT OR UPDATE ON food_truck_presence
    FOR EACH ROW
    EXECUTE FUNCTION food_truck_presence_record_follow_change();
