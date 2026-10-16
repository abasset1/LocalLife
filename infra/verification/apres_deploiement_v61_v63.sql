-- Contrôle après déploiement des migrations V61 à V63 (ADR-0011, lieux permanents) — LECTURE SEULE.
-- Usage (VPS, depuis infra/), sans variable à préparer : le conteneur connaît ses identifiants.
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/apres_deploiement_v61_v63.sql
-- Aucune écriture : transaction en lecture seule, uniquement des SELECT, aucune donnée personnelle affichée.

BEGIN TRANSACTION READ ONLY;

\echo '1. Migrations V60 à V63 (attendu : 4 lignes, success = t)'
SELECT version, description, success, installed_on::date AS installee_le
FROM flyway_schema_history
WHERE version IN ('60', '61', '62', '63')
ORDER BY installed_rank;

\echo '2. Sources DATAtourisme (attendu : la source « lieux » existe, INACTIVE tant que non activée)'
SELECT id, name, type, status, last_sync_at::date AS derniere_synchro
FROM source
WHERE type IN ('DATATOURISME', 'DATATOURISME_LIEUX')
ORDER BY id;

\echo '3. Précision des positions (V61 : APPROXIMATE pour les textes génériques, le reste UNKNOWN)'
SELECT 'lieux d''événement' AS quoi, coordinate_precision, count(*)
FROM location WHERE place_status IS NULL GROUP BY coordinate_precision
UNION ALL
SELECT 'activités', coordinate_precision, count(*)
FROM activity GROUP BY coordinate_precision
ORDER BY 1, 2;

\echo '4. Lieux permanents par statut (attendu juste après le déploiement : aucune ligne)'
SELECT place_status, count(*) FROM location WHERE place_status IS NOT NULL GROUP BY place_status ORDER BY 1;

\echo '5. Liens lieu d''événement → lieu permanent (V63 ; attendu juste après le déploiement : 0)'
SELECT count(*) AS liens FROM location WHERE place_id IS NOT NULL;

\echo '6. Activités par statut (à comparer avec le relevé d''avant déploiement)'
SELECT status, count(*) FROM activity GROUP BY status ORDER BY 1;

ROLLBACK;
