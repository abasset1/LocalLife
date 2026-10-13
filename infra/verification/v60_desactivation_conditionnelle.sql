-- Désactivation ciblée de la source DATAtourisme créée par V60 — UNIQUEMENT si elle n'a jamais servi.
-- À n'exécuter qu'après v60_diagnostic.sql, dans un environnement au verdict B1 (ou A, après déploiement).
-- Ce n'est pas une migration Flyway : rien ne s'exécute automatiquement.
-- Usage (VPS, depuis infra/, après « set -a; . ./.env.beta; set +a » comme backup.sh) :
--   docker compose -f docker-compose.beta.yml exec -T postgres \
--     psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 < verification/v60_desactivation_conditionnelle.sql
--
-- Ne touche qu'une ligne de source, et seulement si toutes les conditions tiennent :
-- source de V60 (type et URL), ACTIVE, jamais synchronisée, sans aucune activité ni empreinte d'import.
-- Une source ayant déjà alimenté la base (verdict B2) reste inchangée : 0 ligne modifiée.
-- Aucune activité n'est modifiée ni supprimée. Réactivation : « Essai à blanc » puis « Activer après vérification ».

BEGIN;

UPDATE source s
SET status = 'INACTIVE'
WHERE s.type = 'DATATOURISME'
  AND s.status = 'ACTIVE'
  AND s.last_sync_at IS NULL
  AND s.url = 'https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80'
  AND NOT EXISTS (SELECT 1 FROM activity a WHERE a.source_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM import_fingerprint f WHERE f.source_id = s.id);

\echo '== Sources DATAtourisme après mise à jour =='
SELECT s.id, s.name, s.status, s.last_sync_at,
       (SELECT count(*) FROM activity a WHERE a.source_id = s.id) AS activites
FROM source s
WHERE s.type = 'DATATOURISME'
ORDER BY s.id;

COMMIT;
