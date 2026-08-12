-- LL-4002 : recherche géographique PostGIS.
-- L'image Docker postgres (infra/docker-compose.yml) est déjà postgis/postgis:16-3.4.

CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE activity ADD COLUMN location GEOGRAPHY(Point, 4326);

-- Colonne alimentée automatiquement depuis latitude/longitude (colonnes
-- existantes, conservées comme source de vérité) : ainsi ni le domaine
-- Activity ni ActivityService n'ont besoin d'être modifiés pour ce ticket.
CREATE OR REPLACE FUNCTION activity_set_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    ELSE
        NEW.location := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_activity_set_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON activity
    FOR EACH ROW
    EXECUTE FUNCTION activity_set_location();

-- Rétro-remplissage des activités de démo déjà en base (le trigger ne
-- s'applique qu'aux futurs INSERT/UPDATE).
UPDATE activity
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

CREATE INDEX idx_activity_location ON activity USING GIST (location);
