-- Carte « Night » de l'Explorer : activités qui ont lieu entre 20 h et 6 h
-- (soirées, concerts, festivals…). Une occurrence (heures locales du lieu)
-- est « de nuit » quand :
--   * elle commence entre 20 h et 6 h ;
--   * ou elle commence dans la journée et se termine après 20 h le même jour,
--     ou passe minuit, à condition de durer moins de 24 h (une exposition de
--     plusieurs semaines n'est pas un événement de nuit).
-- Un début à 00:00 exactement est la convention des imports sans horaire
-- (journée entière, `atStartOfDay`) : l'heure est inconnue, l'occurrence n'est
-- pas retenue. Même chose pour une fin à 23:59 (fin de journée sans horaire).
CREATE OR REPLACE FUNCTION ll_is_night(start_local TIMESTAMP, end_local TIMESTAMP)
    RETURNS BOOLEAN
    LANGUAGE sql
    IMMUTABLE
AS $$
    SELECT start_local IS NOT NULL
       AND start_local::time <> TIME '00:00'
       AND (start_local::time >= TIME '20:00'
            OR start_local::time < TIME '06:00'
            OR (end_local IS NOT NULL
                AND end_local > start_local
                AND end_local - start_local < INTERVAL '24 hours'
                AND (end_local::date > start_local::date
                     OR (end_local::time > TIME '20:00' AND end_local::time < TIME '23:59'))))
$$;

-- La carte « Night » peut être choisie comme carte ouverte par défaut.
ALTER TABLE user_preferences DROP CONSTRAINT IF EXISTS user_preferences_default_map_layer_check;
ALTER TABLE user_preferences
    ADD CONSTRAINT user_preferences_default_map_layer_check
        CHECK (default_map_layer IN ('activities', 'places', 'foodTrucks', 'night'));
