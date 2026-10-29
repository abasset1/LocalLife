-- Durée de l'import planifié : où passe le temps ? — LECTURE SEULE.
-- Usage (VPS, depuis infra/), de préférence juste après la fin d'un import (ligne « Import planifie termine ») :
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/import_duree_mesure.sql > import-duree.txt 2>&1
-- Contexte : un tour d'import dure environ 1 h 37 (4 615 agendas, ~100 000 événements, 28/10/2026) alors qu'il est
-- déclenché toutes les heures : un déclenchement sur deux est ignoré.
-- source_sync_state garde, pour chaque agenda, le DERNIER essai : fin (last_attempt_at), durée (last_duration_ms :
-- collecte, traitement et archivage de cet agenda), pages lues et événements reçus. Le dernier tour complet est
-- reconstitué en remontant depuis la fin du plus récent essai.
-- Temps « hors agenda » : écart entre la fin d'un agenda et celle du précédent, moins la durée du second ; il couvre ce
-- qui se passe entre deux agendas (préparation du lot de classification, enregistrement de l'état…).

\set ON_ERROR_ROLLBACK on
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '120s';
SET LOCAL lock_timeout = '5s';

-- Dernier tour reconstitué (une transaction en lecture seule interdit une vue temporaire : bloc WITH partagé).
\set tour 'WITH dernier_tour AS (SELECT st.*, s.name, st.last_attempt_at - lag(st.last_attempt_at) OVER (ORDER BY st.last_attempt_at) AS ecart FROM source_sync_state st JOIN source s ON s.id = st.source_id WHERE s.status = ''ACTIVE'' AND st.last_attempt_at > (SELECT max(last_attempt_at) FROM source_sync_state) - interval ''3 hours'')'

\echo '== 1. Le dernier tour : durée réelle, somme des durées par agenda, temps hors agenda =='
:tour
SELECT count(*) AS agendas,
       sum(last_received) AS evenements_recus,
       sum(last_pages_read) AS pages_lues,
       (max(last_attempt_at) - min(last_attempt_at))::interval(0) AS duree_premier_au_dernier,
       (sum(last_duration_ms) / 1000)::int * interval '1 second' AS somme_durees_agendas,
       ((extract(epoch FROM max(last_attempt_at) - min(last_attempt_at)) * 1000 - sum(last_duration_ms)) / 1000)::int
           * interval '1 second' AS temps_hors_agenda,
       round(avg(last_duration_ms))::int AS ms_moyen_par_agenda,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY last_duration_ms))::int AS ms_median_par_agenda
FROM dernier_tour;

\echo '== 2. Temps hors agenda : écart moyen entre deux agendas, une fois retirée la durée de l''agenda =='
:tour
SELECT round(avg(extract(epoch FROM ecart) * 1000 - last_duration_ms))::int AS ms_hors_agenda_moyen,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM ecart) * 1000 - last_duration_ms))::int
           AS ms_hors_agenda_median
FROM dernier_tour
WHERE ecart IS NOT NULL;

\echo '== 3. Répartition par volume reçu : combien d''agendas, combien de temps =='
:tour
SELECT CASE WHEN last_received = 0 THEN 'a. 0 événement'
            WHEN last_received <= 20 THEN 'b. 1 à 20'
            WHEN last_received <= 300 THEN 'c. 21 à 300 (1 page)'
            WHEN last_received <= 1500 THEN 'd. 301 à 1 500'
            ELSE 'e. plus de 1 500' END AS volume,
       count(*) AS agendas,
       sum(last_received) AS evenements,
       (sum(last_duration_ms) / 1000)::int * interval '1 second' AS temps_total,
       round(100.0 * sum(last_duration_ms) / sum(sum(last_duration_ms)) OVER ())::int AS part_du_temps_pct,
       round(avg(last_duration_ms))::int AS ms_moyen
FROM dernier_tour
GROUP BY 1
ORDER BY 1;

\echo '== 4. Les 25 agendas les plus longs =='
:tour
SELECT source_id, left(name, 45) AS agenda, last_received AS recus, last_pages_read AS pages,
       (last_duration_ms / 1000.0)::numeric(8, 1) AS secondes,
       CASE WHEN last_received > 0 THEN round(last_duration_ms::numeric / last_received, 1) END AS ms_par_evenement,
       last_status
FROM dernier_tour
ORDER BY last_duration_ms DESC
LIMIT 25;

\echo '== 5. Agendas vides : publient-ils encore quelque chose ? =='
\echo '   sans_activite_publiee : agendas vides au dernier tour ET sans aucune activité publiée (agendas éteints).'
:tour
SELECT count(*) AS agendas_vides,
       count(*) FILTER (WHERE NOT EXISTS (
           SELECT 1 FROM activity a WHERE a.source_id = d.source_id AND a.status = 'PUBLISHED')) AS sans_activite_publiee,
       (sum(last_duration_ms) / 1000)::int * interval '1 second' AS temps_total,
       round(avg(last_duration_ms))::int AS ms_moyen
FROM dernier_tour d
WHERE last_received = 0;

\echo '== 6. Agendas « archives » ou millésimés passés (noms contenant une année de 2015 à 2024, ou « archive ») =='
:tour
SELECT count(*) AS agendas,
       count(*) FILTER (WHERE last_received = 0) AS vides,
       sum(last_received) AS evenements,
       (sum(last_duration_ms) / 1000)::int * interval '1 second' AS temps_total
FROM dernier_tour
WHERE name ~* '(archive|20(1[5-9]|2[0-4]))';

ROLLBACK;
