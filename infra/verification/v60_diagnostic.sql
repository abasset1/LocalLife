-- Diagnostic de la migration V60 (source DATAtourisme) — LECTURE SEULE.
-- Usage (VPS, depuis infra/, après « set -a; . ./.env.beta; set +a » comme backup.sh) :
--   docker compose -f docker-compose.beta.yml exec -T postgres \
--     psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 < verification/v60_diagnostic.sql
-- (ou : psql "$DATABASE_URL" -f infra/verification/v60_diagnostic.sql)
-- Aucune écriture : transaction en lecture seule, uniquement des SELECT.
-- Procédure et interprétation : docs/02_Architecture/DATAGOUV_SOURCES.md, « Migration V60 ».

BEGIN TRANSACTION READ ONLY;

\echo '== 1. V60 dans l''historique Flyway =='
SELECT version, checksum, installed_on, success
FROM flyway_schema_history
WHERE version = '60';

\echo '== 2. Sources DATAtourisme =='
SELECT s.id, s.name, s.status, s.last_sync_at, s.url, s.region_filter,
       (SELECT count(*) FROM activity a WHERE a.source_id = s.id) AS activites,
       (SELECT count(*) FROM import_fingerprint f WHERE f.source_id = s.id) AS empreintes_import
FROM source s
WHERE s.type = 'DATATOURISME'
ORDER BY s.id;

\echo '== 3. Activités DATAtourisme par statut =='
SELECT a.status, count(*)
FROM activity a
JOIN source s ON s.id = a.source_id
WHERE s.type = 'DATATOURISME'
GROUP BY a.status
ORDER BY a.status;

\echo '== 4. Verdict =='
WITH v60 AS (
    SELECT checksum FROM flyway_schema_history WHERE version = '60' AND success
), src AS (
    SELECT s.id, s.status, s.last_sync_at,
           EXISTS (SELECT 1 FROM activity a WHERE a.source_id = s.id) AS a_des_activites,
           EXISTS (SELECT 1 FROM import_fingerprint f WHERE f.source_id = s.id) AS a_des_empreintes
    FROM source s
    WHERE s.type = 'DATATOURISME'
      AND s.url = 'https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80'
)
SELECT CASE
           WHEN NOT EXISTS (SELECT 1 FROM v60) THEN 'A : V60 non appliquée'
           WHEN (SELECT checksum FROM v60) = 1901026672
               THEN 'C : version abandonnée de V60 (développement) — voir la procédure, sauvegarde avant tout repair'
           WHEN (SELECT checksum FROM v60) <> -753629161
               THEN 'INCONNU : somme de contrôle inattendue — ne rien réparer, analyser'
           WHEN NOT EXISTS (SELECT 1 FROM src) THEN 'B0 : V60 appliquée, source supprimée ou modifiée depuis'
           WHEN (SELECT last_sync_at IS NULL AND NOT a_des_activites AND NOT a_des_empreintes FROM src)
               THEN 'B1 : source jamais utilisée (statut ' || (SELECT status FROM src) || ')'
           ELSE 'B2 : source ayant déjà alimenté la base (statut ' || (SELECT status FROM src) || ')'
       END AS verdict;

ROLLBACK;
