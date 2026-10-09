-- Date de publication des activités : instant du premier passage au statut
-- PUBLISHED (création directe en PUBLISHED par un import, ou validation d'une
-- contribution par un administrateur).
--
-- Renseignée par un trigger plutôt que par l'application : tous les chemins
-- d'écriture sont couverts (ActivityService, ImportService, SQL direct), et
-- la valeur ne peut plus être modifiée une fois posée — une activité
-- archivée puis republiée garde sa première date de publication. Côté Java,
-- la propriété est en lecture seule (Activity#publishedAt).
--
-- Activités déjà publiées avant cette migration : date inconnue, laissée à
-- NULL (aucune valeur inventée).
ALTER TABLE activity ADD COLUMN published_at TIMESTAMP;

CREATE OR REPLACE FUNCTION activity_set_published_at()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        NEW.published_at := COALESCE(OLD.published_at, NEW.published_at);
    END IF;
    IF NEW.status = 'PUBLISHED' AND NEW.published_at IS NULL THEN
        NEW.published_at := now();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_activity_set_published_at
    BEFORE INSERT OR UPDATE ON activity
    FOR EACH ROW
    EXECUTE FUNCTION activity_set_published_at();

CREATE INDEX idx_activity_published_at ON activity(published_at);
