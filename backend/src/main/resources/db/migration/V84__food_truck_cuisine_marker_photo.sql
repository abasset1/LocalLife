-- Food trucks : type de cuisine filtrable sur la carte et la liste, pastille personnalisée et photo.
--
-- cuisine_type : code d'une liste fermée (FoodTruckCuisine), nul pour les food trucks existants ; category reste la
-- spécialité saisie librement (« Tacos maison »…).
-- marker_emoji / marker_color : pastille choisie par le propriétaire ; nulles = pastille par défaut (🚚, corail).
-- food_truck_photo : une photo par food truck, stockée en base (pas de service de stockage de fichiers), servie par
-- GET /api/v1/foodtrucks/{id}/photo.

ALTER TABLE food_truck
    ADD COLUMN cuisine_type VARCHAR(30),
    ADD COLUMN marker_emoji VARCHAR(16),
    ADD COLUMN marker_color VARCHAR(7) CHECK (marker_color IS NULL OR marker_color ~ '^#[0-9a-fA-F]{6}$');

CREATE INDEX idx_food_truck_cuisine_type ON food_truck (cuisine_type) WHERE cuisine_type IS NOT NULL;

CREATE TABLE food_truck_photo (
    food_truck_id BIGINT PRIMARY KEY REFERENCES food_truck (id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL,
    data BYTEA NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
