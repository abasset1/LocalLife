-- Contrôle V77 / source DATAtourisme « vide-greniers et brocantes » — LECTURE SEULE.
-- Usage (VPS, depuis infra/, comme backup.sh) :
--   set -a && . ./.env.beta && set +a
--   docker compose --env-file .env.beta -f docker-compose.beta.yml exec -T postgres \
--     psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < verification/v77_controle.sql > v77-controle.txt 2>&1
-- Transaction READ ONLY : toute écriture serait refusée. Aucune donnée personnelle affichée.

\set ON_ERROR_ROLLBACK on
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '120s';
SET LOCAL lock_timeout = '5s';

\echo '== 1. Historique Flyway : V75 à la dernière (attendu : V77 success = t) =='
SELECT installed_rank, version, description, checksum, success, installed_on
FROM flyway_schema_history
WHERE version ~ '^[0-9]+$' AND version::int >= 75
ORDER BY installed_rank;

\echo '== 2. Colonne event_selection présente (attendu : 1 ligne) =='
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_name = 'source' AND column_name = 'event_selection';

\echo '== 3. Sources DATAtourisme (attendu : source V77 INACTIVE, last_sync_at NULL, 0 activité, 0 empreinte) =='
SELECT s.id, s.name, s.status, s.url, s.region_filter, s.event_selection, s.last_sync_at,
       (SELECT count(*) FROM activity a WHERE a.source_id = s.id) AS activites,
       (SELECT count(*) FROM activity a WHERE a.source_id = s.id AND a.status = 'PUBLISHED') AS publiees,
       (SELECT count(*) FROM import_fingerprint f WHERE f.source_id = s.id) AS empreintes
FROM source s
WHERE s.type = 'DATATOURISME'
ORDER BY s.id;

\echo '== 4. Catégorie par défaut de la source V77 (attendu : MARKETS) =='
SELECT s.id, c.code
FROM source s
JOIN source_default_category d ON d.source_id = s.id
JOIN category c ON c.id = d.category_id
WHERE s.event_selection = 'VIDE_GRENIERS_BROCANTES';

\echo '== 5. Dernier essai de collecte des sources DATAtourisme (attendu pour V77 : aucune ligne) =='
SELECT st.source_id, st.last_attempt_at, st.last_status, st.last_received, st.last_duration_ms,
       left(st.last_message, 200) AS message
FROM source_sync_state st
JOIN source s ON s.id = st.source_id
WHERE s.type = 'DATATOURISME'
ORDER BY st.source_id;

\echo '== 6. Base de comparaison : vide-greniers/brocantes publiés à venir, par source =='
\echo '   Mots-clés du titre de DataTourismeEventSelection, sans ses exclusions (les types DATAtourisme ne sont pas en base).'
SELECT s.id AS source_id, s.type, left(s.name, 60) AS source, count(*) AS a_venir,
       count(DISTINCT left(a.postal_code, 2)) AS departements
FROM activity a
JOIN source s ON s.id = a.source_id
WHERE a.status = 'PUBLISHED'
  AND coalesce(a.end_date, a.start_date) >= CURRENT_DATE
  AND ll_search_normalize(a.title)
      ~ '\m(vide ?greniers?|brocantes?|braderies?|puces|vide ?dressings?|bourses? aux|troc et puces|foires? a tout|bric a brac|vide (jardins?|maisons?|poussettes?|ta chambre|chambres?)|bourses? (de |a la )?puericulture)\M'
GROUP BY s.id, s.type, s.name
ORDER BY 4 DESC
LIMIT 40;

ROLLBACK;
