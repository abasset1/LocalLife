-- Contrôle général de la base bêta — LECTURE SEULE.
-- Usage (VPS, depuis infra/), sans variable à préparer : le conteneur connaît ses identifiants.
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/controle_beta.sql > controle-beta.txt
-- (ou, via un tunnel SSH : psql "$DATABASE_URL" -f infra/verification/controle_beta.sql)
-- Aucune écriture : transaction en lecture seule, uniquement des SELECT, aucune donnée personnelle affichée
-- (ni e-mail ni nom : uniquement des comptes et des identifiants techniques).
-- Les sections qui lisent des tables récentes ne s'exécutent que si la migration correspondante est appliquée :
-- le script fonctionne donc aussi sur une bêta en retard sur main (dernière migration de main : V74).
-- Pour une analyse détaillée de V60 et V61 à V63 : v60_diagnostic.sql et apres_deploiement_v61_v63.sql.

-- Une requête en erreur (délai dépassé, verrou) n'interrompt pas les suivantes : psql pose un point de reprise
-- avant chacune. lock_timeout distingue une table verrouillée par une autre session d'une requête lente.
\set ON_ERROR_ROLLBACK on

BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '60s';
SET LOCAL lock_timeout = '5s';

\echo '== 0. Sessions en cours sur la base (attendu : aucune transaction ouverte depuis plus de 5 minutes)'
SELECT pid, usename, state, wait_event_type, wait_event, (now() - xact_start)::interval(0) AS transaction_depuis,
       left(regexp_replace(query, '\s+', ' ', 'g'), 100) AS requete
FROM pg_stat_activity
WHERE datname = current_database() AND pid <> pg_backend_pid() AND xact_start IS NOT NULL
ORDER BY xact_start;

\echo '0b. Verrous posés ou attendus sur les tables principales'
SELECT l.pid, c.relname AS table_, l.mode, l.granted
FROM pg_locks l
JOIN pg_class c ON c.oid = l.relation
WHERE c.relname IN ('activity', 'source', 'location', 'schedule', 'occurrence', 'users')
  AND l.pid <> pg_backend_pid()
ORDER BY c.relname, l.granted, l.pid;

\echo '== 1. Migrations Flyway =='
\echo '1a. Dernière migration appliquée (attendu : 74 si la bêta est à jour de main)'
SELECT max(version::int) AS derniere_version, count(*) AS migrations, max(installed_on)::date AS derniere_le
FROM flyway_schema_history
WHERE success AND version IS NOT NULL;

\echo '1b. Migrations en échec (attendu : aucune ligne)'
SELECT installed_rank, version, description, installed_on
FROM flyway_schema_history
WHERE NOT success
ORDER BY installed_rank;

\echo '1c. Dix dernières migrations'
SELECT version, description, installed_on::date AS installee_le, execution_time AS ms
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

SELECT coalesce(max(version::int), 0) >= 60 AS v60,
       coalesce(max(version::int), 0) >= 63 AS v63,
       coalesce(max(version::int), 0) >= 68 AS v68,
       coalesce(max(version::int), 0) >= 69 AS v69,
       coalesce(max(version::int), 0) >= 73 AS v73,
       coalesce(max(version::int), 0) >= 74 AS v74
FROM flyway_schema_history
WHERE success AND version IS NOT NULL \gset

\echo '== 2. Sources =='
\echo '2a. Sources, activités rattachées et dernière synchronisation'
SELECT s.id, s.name, s.type, s.status, s.last_sync_at::date AS derniere_synchro, coalesce(a.nb, 0) AS activites
FROM source s
LEFT JOIN (SELECT source_id, count(*) AS nb FROM activity GROUP BY source_id) a ON a.source_id = s.id
ORDER BY s.id;

\if :v68
\echo '2b. État de la dernière collecte par source (V68 ; attendu : SUCCESS, tentative de moins de 2 h)'
SELECT s.id, s.name, st.last_status, st.last_attempt_at, st.last_received, st.last_duration_ms AS ms,
       left(st.last_message, 120) AS message
FROM source_sync_state st
JOIN source s ON s.id = st.source_id
ORDER BY st.last_status DESC, s.id;

\echo '2c. Sources actives sans tentative de collecte depuis 2 h (import planifié toutes les heures ; attendu : aucune ligne)'
SELECT s.id, s.name, s.type, st.last_attempt_at
FROM source s
LEFT JOIN source_sync_state st ON st.source_id = s.id
WHERE s.status = 'ACTIVE' AND s.type <> 'MANUAL'
  AND (st.last_attempt_at IS NULL OR st.last_attempt_at < now() - interval '2 hours')
ORDER BY s.id;
\endif

\if :v60
\echo '2d. Source DATAtourisme de V60 (détail et verdict : v60_diagnostic.sql)'
SELECT s.id, s.status, s.last_sync_at,
       (SELECT count(*) FROM activity a WHERE a.source_id = s.id) AS activites
FROM source s
WHERE s.type = 'DATATOURISME'
ORDER BY s.id;
\endif

\echo '== 3. Activités =='
\echo '3a. Activités par statut'
SELECT status, count(*) FROM activity GROUP BY status ORDER BY 1;

\echo '3b. Activités publiées : à venir ou en cours / passées'
SELECT count(*) FILTER (WHERE coalesce(end_date, start_date) >= CURRENT_DATE) AS a_venir_ou_en_cours,
       count(*) FILTER (WHERE coalesce(end_date, start_date) < CURRENT_DATE) AS passees,
       count(*) FILTER (WHERE start_date IS NULL) AS sans_date
FROM activity
WHERE status = 'PUBLISHED';

\echo '3c. Anomalies sur les activités publiées (attendu : 0 partout)'
SELECT count(*) FILTER (WHERE end_date < start_date) AS fin_avant_debut,
       count(*) FILTER (WHERE latitude IS NULL OR longitude IS NULL) AS sans_position,
       count(*) FILTER (WHERE latitude NOT BETWEEN -90 AND 90 OR longitude NOT BETWEEN -180 AND 180)
           AS position_invalide,
       count(*) FILTER (WHERE btrim(coalesce(title, '')) = '') AS sans_titre,
       count(*) FILTER (WHERE source_id IS NULL) AS sans_source
FROM activity
WHERE status = 'PUBLISHED';

\echo '3d. Activités publiées sans programmation (schedule), par source'
SELECT s.name AS source, count(*) AS activites
FROM activity a
LEFT JOIN source s ON s.id = a.source_id
WHERE a.status = 'PUBLISHED'
  AND NOT EXISTS (SELECT 1 FROM schedule sc WHERE sc.activity_id = a.id)
GROUP BY s.name
ORDER BY 2 DESC;

\echo '3e. Activités publiées à venir par code postal (10 premiers)'
SELECT coalesce(left(postal_code, 2), '??') AS dep, count(*)
FROM activity
WHERE status = 'PUBLISHED' AND coalesce(end_date, start_date) >= CURRENT_DATE
GROUP BY 1
ORDER BY 2 DESC
LIMIT 10;

\echo '3f. Doublons exacts probables : même titre, même date de début, même source (20 premiers ; attendu : aucune ligne)'
SELECT a.source_id, lower(btrim(a.title)) AS titre, a.start_date::date AS debut, count(*) AS nb,
       string_agg(a.id::text, ', ' ORDER BY a.id) AS ids
FROM activity a
WHERE a.status = 'PUBLISHED'
GROUP BY a.source_id, lower(btrim(a.title)), a.start_date::date
HAVING count(*) > 1
ORDER BY nb DESC
LIMIT 20;

\if :v73
\echo '3g. Corbeille (V73) : activités supprimées'
SELECT count(*) AS supprimees, min(deleted_at)::date AS plus_ancienne, max(deleted_at)::date AS plus_recente
FROM activity
WHERE status = 'DELETED';
\endif

\if :v63
\echo '== 4. Lieux (V61 à V63) =='
\echo '4a. Lieux permanents par statut'
SELECT place_status, count(*) FROM location WHERE place_status IS NOT NULL GROUP BY place_status ORDER BY 1;

\echo '4b. Précision des positions des activités'
SELECT coordinate_precision, count(*) FROM activity GROUP BY coordinate_precision ORDER BY 1;

\echo '4c. Lieux d''événement reliés à un lieu permanent'
SELECT count(*) AS liens FROM location WHERE place_id IS NOT NULL;
\endif

\echo '== 5. Comptes (aucune donnée personnelle) =='
\echo '5a. Comptes par rôle et vérification de l''adresse'
SELECT role, email_verified, count(*) FROM users GROUP BY role, email_verified ORDER BY 1, 2;

\if :v69
\echo '5b. Comptes par statut (V69)'
SELECT account_status, count(*) FROM users GROUP BY account_status ORDER BY 1;
\endif

\echo '5c. Au moins un administrateur (attendu : >= 1)'
SELECT count(*) AS administrateurs FROM users WHERE role = 'ADMIN';

\if :v74
\echo '5d. RGPD (V74) : consentement et durées de conservation (purge chaque nuit ; non_confirmes_30_jours attendu : 0)'
SELECT count(*) FILTER (WHERE terms_accepted_at IS NOT NULL) AS cgu_acceptees,
       count(*) FILTER (WHERE terms_accepted_at IS NULL) AS sans_consentement,
       count(*) FILTER (WHERE last_active_at < now() - interval '3 years' AND role <> 'ADMIN') AS inactifs_3_ans,
       count(*) FILTER (WHERE inactivity_warned_at IS NOT NULL) AS prevenus_inactivite,
       count(*) FILTER (WHERE NOT email_verified AND created_at < now() - interval '30 days') AS non_confirmes_30_jours
FROM users;
\endif

\if :v73
\echo '== 6. Journal d''administration (V73) : actions des 30 derniers jours =='
SELECT action, target_type, count(*), max(occurred_at)::date AS derniere
FROM admin_action_log
WHERE occurred_at >= now() - interval '30 days'
GROUP BY action, target_type
ORDER BY 3 DESC;
\endif

\echo '== 7. Volumétrie =='
SELECT relname AS table_, n_live_tup AS lignes_estimees, pg_size_pretty(pg_total_relation_size(relid)) AS taille
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 15;
SELECT pg_size_pretty(pg_database_size(current_database())) AS taille_base;

ROLLBACK;
