-- LL-11001 (Sprint 11) : modèle Location, indépendant de Activity — voir
-- docs/05_Sprints/SPRINT_11.md, section 3, et la javadoc de Location.java.
-- Aucune colonne de activity n'est modifiée par cette migration (critère
-- d'acceptation « compatibilité avec les données existantes ») : le lien
-- vers Activity/Schedule est du périmètre de LL-11002, pas de celui-ci.

CREATE TABLE location (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    address VARCHAR(512),
    postal_code VARCHAR(20),
    city VARCHAR(255),
    department VARCHAR(255),
    region VARCHAR(255),
    country_code VARCHAR(2),
    insee VARCHAR(10),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    timezone VARCHAR(64),
    website VARCHAR(512),
    email VARCHAR(255),
    phone VARCHAR(50)
);

-- Même patron que activity.location (V7__add_postgis_location_to_activity.sql) :
-- colonne géographique alimentée automatiquement depuis latitude/longitude,
-- conservées comme source de vérité — le domaine/service Location n'a donc
-- jamais à la renseigner explicitement.
ALTER TABLE location ADD COLUMN geo_location GEOGRAPHY(Point, 4326);

CREATE OR REPLACE FUNCTION location_set_geo_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.geo_location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    ELSE
        NEW.geo_location := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_location_set_geo_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON location
    FOR EACH ROW
    EXECUTE FUNCTION location_set_geo_location();

CREATE INDEX idx_location_geo_location ON location USING GIST (geo_location);
