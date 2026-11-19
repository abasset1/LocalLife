-- Notifications de suivi : dire ce qui a changé, pas seulement « dates modifiées » ou « informations mises à jour ».
--
-- follow_change.details : un objet par champ modifié, dans l'ordre du trigger :
--   {"field": "start_date", "old": "2026-11-22T20:30:00", "new": "2026-11-23T20:30:00"}
-- Valeurs en texte, coupées à 300 caractères. Pour les textes longs (descriptions), seul le champ est noté
-- (old et new nuls) : la notification dit « description mise à jour » sans recopier le texte.
-- Les changements enregistrés avant cette migration ont une liste vide : leur notification reste celle d'avant.
--
-- Champs : activité — start_date, end_date, address (adresse, code postal et ville réunis), position (coordonnées
-- seules), title, description, long_description, conditions, age_min, age_max, url ; lieu — address, position, name,
-- description, website, email, phone ; food truck — name, description, category, cuisine_type, contact,
-- instagram_url, facebook_url. Les kinds et les cas qui déclenchent un changement ne changent pas (V79, V91).
ALTER TABLE follow_change ADD COLUMN details JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE OR REPLACE FUNCTION ll_follow_field(field TEXT, old_value TEXT, new_value TEXT)
RETURNS JSONB AS $$
    SELECT jsonb_build_object('field', field, 'old', left(old_value, 300), 'new', left(new_value, 300));
$$ LANGUAGE sql IMMUTABLE;

-- Adresse lisible : « 1 rue de la République, 84000 Avignon ».
CREATE OR REPLACE FUNCTION ll_follow_address(address TEXT, postal_code TEXT, city TEXT)
RETURNS TEXT AS $$
    SELECT NULLIF(concat_ws(', ', NULLIF(trim(address), ''),
                            NULLIF(trim(concat_ws(' ', NULLIF(trim(postal_code), ''), NULLIF(trim(city), ''))), '')),
                  '');
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION ll_follow_timestamp(value TIMESTAMP)
RETURNS TEXT AS $$
    SELECT to_char(value, 'YYYY-MM-DD"T"HH24:MI:SS');
$$ LANGUAGE sql IMMUTABLE;

-- Activité (V79) : mêmes cas, avec le détail des champs.
CREATE OR REPLACE FUNCTION activity_record_follow_change()
RETURNS TRIGGER AS $$
DECLARE
    changes TEXT[] := ARRAY[]::TEXT[];
    fields JSONB := '[]'::jsonb;
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
            IF NEW.start_date IS DISTINCT FROM OLD.start_date THEN
                fields := fields || ll_follow_field('start_date', ll_follow_timestamp(OLD.start_date::timestamp),
                                                    ll_follow_timestamp(NEW.start_date::timestamp));
            END IF;
            IF NEW.end_date IS DISTINCT FROM OLD.end_date THEN
                fields := fields || ll_follow_field('end_date', ll_follow_timestamp(OLD.end_date::timestamp),
                                                    ll_follow_timestamp(NEW.end_date::timestamp));
            END IF;
        END IF;
        IF NEW.address IS DISTINCT FROM OLD.address OR NEW.city IS DISTINCT FROM OLD.city
                OR NEW.postal_code IS DISTINCT FROM OLD.postal_code
                OR NEW.latitude IS DISTINCT FROM OLD.latitude OR NEW.longitude IS DISTINCT FROM OLD.longitude THEN
            changes := array_append(changes, 'LOCATION');
            IF ll_follow_address(NEW.address, NEW.postal_code, NEW.city)
                    IS DISTINCT FROM ll_follow_address(OLD.address, OLD.postal_code, OLD.city) THEN
                fields := fields || ll_follow_field('address', ll_follow_address(OLD.address, OLD.postal_code, OLD.city),
                                                    ll_follow_address(NEW.address, NEW.postal_code, NEW.city));
            ELSE
                fields := fields || ll_follow_field('position', NULL, NULL);
            END IF;
        END IF;
        IF NEW.title IS DISTINCT FROM OLD.title OR NEW.description IS DISTINCT FROM OLD.description
                OR NEW.long_description IS DISTINCT FROM OLD.long_description
                OR NEW.conditions IS DISTINCT FROM OLD.conditions
                OR NEW.age_min IS DISTINCT FROM OLD.age_min OR NEW.age_max IS DISTINCT FROM OLD.age_max
                OR NEW.url IS DISTINCT FROM OLD.url THEN
            changes := array_append(changes, 'DETAILS');
            IF NEW.title IS DISTINCT FROM OLD.title THEN
                fields := fields || ll_follow_field('title', OLD.title, NEW.title);
            END IF;
            IF NEW.description IS DISTINCT FROM OLD.description THEN
                fields := fields || ll_follow_field('description', NULL, NULL);
            END IF;
            IF NEW.long_description IS DISTINCT FROM OLD.long_description THEN
                fields := fields || ll_follow_field('long_description', NULL, NULL);
            END IF;
            IF NEW.conditions IS DISTINCT FROM OLD.conditions THEN
                fields := fields || ll_follow_field('conditions', OLD.conditions, NEW.conditions);
            END IF;
            IF NEW.age_min IS DISTINCT FROM OLD.age_min THEN
                fields := fields || ll_follow_field('age_min', OLD.age_min::text, NEW.age_min::text);
            END IF;
            IF NEW.age_max IS DISTINCT FROM OLD.age_max THEN
                fields := fields || ll_follow_field('age_max', OLD.age_max::text, NEW.age_max::text);
            END IF;
            IF NEW.url IS DISTINCT FROM OLD.url THEN
                fields := fields || ll_follow_field('url', OLD.url, NEW.url);
            END IF;
        END IF;
    END IF;

    IF cardinality(changes) > 0 THEN
        INSERT INTO follow_change (activity_id, kinds, details) VALUES (NEW.id, changes, fields);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Lieu permanent (V79) : mêmes cas, avec le détail des champs.
CREATE OR REPLACE FUNCTION location_record_follow_change()
RETURNS TRIGGER AS $$
DECLARE
    changes TEXT[] := ARRAY[]::TEXT[];
    fields JSONB := '[]'::jsonb;
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
            IF ll_follow_address(NEW.address, NEW.postal_code, NEW.city)
                    IS DISTINCT FROM ll_follow_address(OLD.address, OLD.postal_code, OLD.city) THEN
                fields := fields || ll_follow_field('address', ll_follow_address(OLD.address, OLD.postal_code, OLD.city),
                                                    ll_follow_address(NEW.address, NEW.postal_code, NEW.city));
            ELSE
                fields := fields || ll_follow_field('position', NULL, NULL);
            END IF;
        END IF;
        IF NEW.name IS DISTINCT FROM OLD.name OR NEW.description IS DISTINCT FROM OLD.description THEN
            changes := array_append(changes, 'DETAILS');
            IF NEW.name IS DISTINCT FROM OLD.name THEN
                fields := fields || ll_follow_field('name', OLD.name, NEW.name);
            END IF;
            IF NEW.description IS DISTINCT FROM OLD.description THEN
                fields := fields || ll_follow_field('description', NULL, NULL);
            END IF;
        END IF;
        IF NEW.website IS DISTINCT FROM OLD.website OR NEW.email IS DISTINCT FROM OLD.email
                OR NEW.phone IS DISTINCT FROM OLD.phone THEN
            changes := array_append(changes, 'CONTACT');
            IF NEW.website IS DISTINCT FROM OLD.website THEN
                fields := fields || ll_follow_field('website', OLD.website, NEW.website);
            END IF;
            IF NEW.email IS DISTINCT FROM OLD.email THEN
                fields := fields || ll_follow_field('email', OLD.email, NEW.email);
            END IF;
            IF NEW.phone IS DISTINCT FROM OLD.phone THEN
                fields := fields || ll_follow_field('phone', OLD.phone, NEW.phone);
            END IF;
        END IF;
    END IF;

    IF cardinality(changes) > 0 THEN
        INSERT INTO follow_change (location_id, kinds, details) VALUES (NEW.id, changes, fields);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Food truck (V91) : mêmes cas, avec le détail des champs. Le planning (food_truck_presence) reste noté sans détail.
CREATE OR REPLACE FUNCTION food_truck_record_follow_change()
RETURNS TRIGGER AS $$
DECLARE
    changes TEXT[] := ARRAY[]::TEXT[];
    fields JSONB := '[]'::jsonb;
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
            IF NEW.name IS DISTINCT FROM OLD.name THEN
                fields := fields || ll_follow_field('name', OLD.name, NEW.name);
            END IF;
            IF NEW.description IS DISTINCT FROM OLD.description THEN
                fields := fields || ll_follow_field('description', NULL, NULL);
            END IF;
            IF NEW.category IS DISTINCT FROM OLD.category THEN
                fields := fields || ll_follow_field('category', OLD.category, NEW.category);
            END IF;
            IF NEW.cuisine_type IS DISTINCT FROM OLD.cuisine_type THEN
                fields := fields || ll_follow_field('cuisine_type', OLD.cuisine_type, NEW.cuisine_type);
            END IF;
        END IF;
        IF NEW.contact IS DISTINCT FROM OLD.contact OR NEW.instagram_url IS DISTINCT FROM OLD.instagram_url
                OR NEW.facebook_url IS DISTINCT FROM OLD.facebook_url THEN
            changes := array_append(changes, 'CONTACT');
            IF NEW.contact IS DISTINCT FROM OLD.contact THEN
                fields := fields || ll_follow_field('contact', OLD.contact, NEW.contact);
            END IF;
            IF NEW.instagram_url IS DISTINCT FROM OLD.instagram_url THEN
                fields := fields || ll_follow_field('instagram_url', OLD.instagram_url, NEW.instagram_url);
            END IF;
            IF NEW.facebook_url IS DISTINCT FROM OLD.facebook_url THEN
                fields := fields || ll_follow_field('facebook_url', OLD.facebook_url, NEW.facebook_url);
            END IF;
        END IF;
    END IF;

    IF cardinality(changes) > 0 THEN
        INSERT INTO follow_change (food_truck_id, kinds, details) VALUES (NEW.id, changes, fields);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
