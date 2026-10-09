-- Paramètres utilisateur : préférences de recherche par défaut de l'Explorer
-- (catégories, ville, rayon), appliquées à l'ouverture de la carte quand
-- l'utilisateur est connecté.
--
-- Une ligne au plus par utilisateur (user_id en clé primaire). Toutes les
-- colonnes sont facultatives : NULL = pas de préférence, l'Explorer garde
-- alors son comportement par défaut (géolocalisation / Marseille, 50 km).
--
-- default_latitude/default_longitude : coordonnées de default_city,
-- géocodées une seule fois à l'enregistrement (GeocodingService) plutôt qu'à
-- chaque ouverture de la carte. Renseignées ensemble ou pas du tout.
--
-- default_radius_km : même borne que la recherche /nearby (1 à 50 km, voir
-- ActivityService#findNearby).
CREATE TABLE user_preferences (
    user_id           BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    default_city      VARCHAR(255),
    default_latitude  DOUBLE PRECISION,
    default_longitude DOUBLE PRECISION,
    default_radius_km INTEGER CHECK (default_radius_km BETWEEN 1 AND 50),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    CHECK ((default_city IS NULL AND default_latitude IS NULL AND default_longitude IS NULL)
        OR (default_city IS NOT NULL AND default_latitude IS NOT NULL AND default_longitude IS NOT NULL))
);

-- Catégories cochées par défaut, référencées par leur code métier
-- (category.code est UNIQUE) : c'est la valeur échangée par l'API et le
-- filtre ?category= de la recherche, jamais l'id technique.
CREATE TABLE user_preference_category (
    user_id       BIGINT      NOT NULL REFERENCES user_preferences(user_id) ON DELETE CASCADE,
    category_code VARCHAR(50) NOT NULL REFERENCES category(code) ON DELETE CASCADE,
    PRIMARY KEY (user_id, category_code)
);
