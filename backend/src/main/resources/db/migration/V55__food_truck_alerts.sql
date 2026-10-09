-- Alertes sur les nouveaux food trucks.
--
-- 1) Date de publication des food trucks, même principe que
--    activity.published_at (V54) : posée par trigger au premier passage au
--    statut PUBLISHED, figée ensuite. Food trucks existants : NULL (date
--    inconnue) — ils ne seront donc jamais envoyés comme « nouveaux ».
ALTER TABLE food_truck ADD COLUMN published_at TIMESTAMP;

CREATE OR REPLACE FUNCTION food_truck_set_published_at()
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

CREATE TRIGGER trg_food_truck_set_published_at
    BEFORE INSERT OR UPDATE ON food_truck
    FOR EACH ROW
    EXECUTE FUNCTION food_truck_set_published_at();

CREATE INDEX idx_food_truck_published_at ON food_truck(published_at);

-- 2) Une alerte peut aussi couvrir les food trucks (en plus ou à la place de
--    ses catégories d'activités). Au moins l'un des deux est exigé par
--    l'application (les catégories sont dans une autre table).
ALTER TABLE activity_alert ADD COLUMN include_food_trucks BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) Food trucks déjà envoyés pour une alerte (jamais deux fois).
CREATE TABLE activity_alert_food_truck_sent (
    alert_id      BIGINT    NOT NULL REFERENCES activity_alert(id) ON DELETE CASCADE,
    food_truck_id BIGINT    NOT NULL REFERENCES food_truck(id) ON DELETE CASCADE,
    sent_at       TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (alert_id, food_truck_id)
);
