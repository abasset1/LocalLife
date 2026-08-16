-- LL-6003 (Sprint 6) : formalise les statuts MVP définis par SPRINT_6.md
-- (PENDING/PUBLISHED/REJECTED). Jusqu'ici activity.status était une
-- VARCHAR(50) libre, sans contrainte, sans valeur par défaut — le code
-- applicatif renseignait déjà toujours une valeur explicite
-- (NormalizationService : PUBLISHED ; ActivityService#createActivity :
-- PENDING), mais rien ne l'imposait au niveau base.
--
-- Le UPDATE de secours ci-dessous ne devrait affecter aucune ligne en
-- pratique (le code applicatif n'a jamais laissé status à NULL), mais
-- protège le NOT NULL qui suit en l'absence d'accès à une base réelle
-- pour le confirmer en sandbox — à vérifier par Alex avant application.
UPDATE activity SET status = 'PENDING' WHERE status IS NULL;

ALTER TABLE activity ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE activity ALTER COLUMN status SET NOT NULL;
ALTER TABLE activity ADD CONSTRAINT chk_activity_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED'));
