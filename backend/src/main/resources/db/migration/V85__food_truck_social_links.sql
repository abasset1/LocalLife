-- Food trucks : liens Instagram et Facebook saisis par le propriétaire, affichés sur la fiche et la popup.
-- Adresses complètes (https://…) normalisées par ProFoodTruckService ; nulles = non renseignées.

ALTER TABLE food_truck
    ADD COLUMN instagram_url VARCHAR(300),
    ADD COLUMN facebook_url VARCHAR(300);
