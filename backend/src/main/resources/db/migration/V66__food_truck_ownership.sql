-- PRO-01 lot C (ADR-0012, décision D6) et FOODTRUCK-01 (ADR-0013) : un food truck appartient à un compte
-- professionnel, qui crée son profil, le publie lui-même (pas de modération préalable), le suspend ou l'archive, et
-- gère ses présences. Un administrateur peut suspendre un food truck ; le propriétaire ne peut alors plus le
-- republier (suspended_by_admin_at), seul un administrateur le rétablit.
--
-- Food trucks existants : sans propriétaire (owner_id nul), statut inchangé.

ALTER TABLE food_truck DROP CONSTRAINT food_truck_status_check;
ALTER TABLE food_truck ADD CONSTRAINT food_truck_status_check CHECK (status IN
    ('DRAFT', 'PENDING', 'PUBLISHED', 'SUSPENDED', 'ARCHIVED', 'REJECTED'));

ALTER TABLE food_truck
    ADD COLUMN owner_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN suspended_by_admin_at TIMESTAMPTZ,
    ADD COLUMN suspended_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_food_truck_owner ON food_truck (owner_id) WHERE owner_id IS NOT NULL;
