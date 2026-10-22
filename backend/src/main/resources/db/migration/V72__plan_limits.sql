-- Limites par offre (décision du 22/10/2026, ROLES_ET_PERMISSIONS.md § « Limites par offre ») : la limite mensuelle
-- des contributions Premium se compte sur la date de création d'une activité, inconnue jusqu'ici. Les activités
-- existantes gardent une date nulle : elles ne comptent dans aucun mois.
ALTER TABLE activity ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE activity ALTER COLUMN created_at SET DEFAULT now();

CREATE INDEX idx_activity_created_by_created_at ON activity (created_by, created_at) WHERE created_by IS NOT NULL;
