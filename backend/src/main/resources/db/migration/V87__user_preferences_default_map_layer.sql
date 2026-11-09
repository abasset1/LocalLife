-- Carte ouverte par défaut quand l'utilisateur connecté arrive sur l'Explorer :
-- activités, lieux ou food trucks (valeurs échangées telles quelles avec le
-- frontend). NULL = pas de préférence : l'Explorer reprend la dernière carte
-- choisie dans le navigateur (activités à défaut).
ALTER TABLE user_preferences
    ADD COLUMN default_map_layer VARCHAR(20)
        CHECK (default_map_layer IN ('activities', 'places', 'foodTrucks'));
