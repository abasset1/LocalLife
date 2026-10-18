-- ADR-0013 (ARCHI-01, décisions du 18/10/2026) : un food truck est un profil, jamais un lieu. Sa position passe
-- dans des présences : un food truck, un emplacement (une ligne de location, ADR-0011 : pas d'entité « lieu »
-- concurrente) et un créneau. Plusieurs food trucks peuvent partager un emplacement à des créneaux distincts.
--
-- Présence active un jour donné : PUBLISHED, food truck PUBLISHED, jour dans [valid_from, valid_until] (bornes
-- facultatives) et dans days_of_week (codes MO..SU séparés par des virgules ; NULL = tous les jours).
-- spot_kind est porté par la présence (décision D5) : un même parking peut être un marché un jour, un événement
-- un autre.

CREATE TABLE food_truck_presence (
    id BIGSERIAL PRIMARY KEY,
    food_truck_id BIGINT NOT NULL REFERENCES food_truck (id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES location (id),
    spot_kind VARCHAR(20) NOT NULL CHECK (spot_kind IN ('MARKET', 'PRIVATE', 'EVENT', 'OTHER')),
    days_of_week VARCHAR(20) CHECK (days_of_week ~ '^(MO|TU|WE|TH|FR|SA|SU)(,(MO|TU|WE|TH|FR|SA|SU))*$'),
    start_time TIME,
    end_time TIME,
    valid_from DATE,
    valid_until DATE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_food_truck_presence_period CHECK (valid_from IS NULL OR valid_until IS NULL
                                                     OR valid_from <= valid_until),
    CONSTRAINT chk_food_truck_presence_hours CHECK ((start_time IS NULL) = (end_time IS NULL))
);

CREATE INDEX idx_food_truck_presence_truck ON food_truck_presence (food_truck_id) WHERE status = 'PUBLISHED';
CREATE INDEX idx_food_truck_presence_location ON food_truck_presence (location_id);

-- Food trucks existants (décision D3) : une présence permanente (sans jours ni dates) à leur position actuelle,
-- sur un emplacement non typé créé pour eux. Affichage, identifiants et alertes déjà envoyées inchangés.
DO $$
DECLARE
    truck RECORD;
    spot_id BIGINT;
BEGIN
    FOR truck IN SELECT id, latitude, longitude FROM food_truck ORDER BY id LOOP
        INSERT INTO location (latitude, longitude, country_code, timezone, coordinate_precision)
        VALUES (truck.latitude, truck.longitude, 'FR', 'Europe/Paris', 'UNKNOWN')
        RETURNING id INTO spot_id;
        INSERT INTO food_truck_presence (food_truck_id, location_id, spot_kind, status)
        VALUES (truck.id, spot_id, 'OTHER', 'PUBLISHED');
    END LOOP;
END $$;

-- La position du food truck n'est plus écrite : elle vit dans ses présences. Les colonnes restent, en lecture
-- seule, jusqu'à ce que tous les clients lisent les présences (suppression dans une migration ultérieure).
ALTER TABLE food_truck ALTER COLUMN latitude DROP NOT NULL, ALTER COLUMN longitude DROP NOT NULL;
