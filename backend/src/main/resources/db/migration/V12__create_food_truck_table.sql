-- LL-6009 (Sprint 6) : table du nouveau module FoodTruck, séparé
-- d'activity (voir FOOD_TRUCK_CONTRACT.md, LL-6008, pour la décision
-- structurante). Contrainte CHECK sur status posée dès la création de la
-- table (contrairement à activity, où V11 est arrivée après coup sur une
-- colonne déjà existante) : mêmes trois valeurs que activity.status
-- (LL-6003), pour rester cohérent si une modération est ajoutée plus
-- tard (voir FoodTruck, javadoc).
CREATE TABLE food_truck (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    category VARCHAR(255) NOT NULL,
    contact VARCHAR(512),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED'))
);

-- Défaut DB volontairement 'PENDING' (le plus prudent : invisible tant
-- que rien n'agit dessus) même si l'application choisit explicitement
-- 'PUBLISHED' à la création (FoodTruckService#createFoodTruck, voir sa
-- javadoc) — ce défaut ne sert que de filet de sécurité si une ligne est
-- un jour insérée sans passer par ce service.
