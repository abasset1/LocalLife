-- Phase A (ADR-0011, § 7.1) : mesure du rattrapage de la précision géographique — LECTURE SEULE.
-- Compte les lieux existants que la règle des textes génériques classerait APPROXIMATE, et les activités
-- concernées. Les autres restent UNKNOWN : aucun lieu n'est jamais classé EXACT par rattrapage.
-- Usage (VPS, depuis infra/, après « set -a; . ./.env.beta; set +a » comme backup.sh) :
--   docker compose -f docker-compose.beta.yml exec -T postgres \
--     psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 < verification/phase_a_precision_mesure.sql
-- Aucune écriture : transaction en lecture seule, uniquement des SELECT. Fonctionne avant la migration de la
-- phase A (ne lit aucune colonne coordinate_precision).
--
-- Le bloc « classification » reproduit CoordinatePrecisionPolicy.isGenericPlaceText (texte examiné :
-- l'adresse, ou le nom à défaut). Il est répété à l'identique dans chaque requête (une transaction en
-- lecture seule interdit une vue temporaire). GenericPlaceSqlParityIntegrationTest vérifie que les copies
-- sont identiques et qu'elles classent comme la règle Java. Toute modification d'un côté doit être reportée
-- de l'autre.

BEGIN TRANSACTION READ ONLY;

\echo '== 1. Lieux classés APPROXIMATE (les autres restent UNKNOWN) et activités concernées =='
\echo '   activites_rattachees : un lieu approximatif est rattaché (programmation ou occurrence) ;'
\echo '   activites_point_du_lieu : en plus, le point affiché est celui de ce lieu (elles seules seraient marquées).'
WITH
-- classification:début
precision_candidate AS (
    SELECT n.id, n.name, n.address, n.city,
           n.t ~ '\m(divers|differents|plusieurs) lieux\M'
           OR (CASE WHEN n.c <> '' THEN btrim(regexp_replace(replace(n.t, n.c, ' '), '\s+', ' ', 'g')) ELSE n.t END)
              ~ '^(en )?(centre ville|centre village|centre du village|le village|dans le village|dans la ville|dans tout le village|dans les rues( du village| de la ville)?)$'
           AS approximate
    FROM (
        SELECT l.id, l.name, l.address, l.city,
               btrim(regexp_replace(translate(lower(coalesce(
                   CASE WHEN btrim(coalesce(l.address, '')) <> '' THEN l.address ELSE l.name END, '')),
                   'àáâãäåçèéêëìíîïñòóôõöùúûüýÿÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ',
                   'aaaaaaceeeeiiiinooooouuuuyyaaaaaaceeeeiiiinooooouuuuy'), '[^a-z0-9]+', ' ', 'g')) AS t,
               btrim(regexp_replace(translate(lower(coalesce(l.city, '')),
                   'àáâãäåçèéêëìíîïñòóôõöùúûüýÿÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ',
                   'aaaaaaceeeeiiiinooooouuuuyyaaaaaaceeeeiiiinooooouuuuy'), '[^a-z0-9]+', ' ', 'g')) AS c
        FROM location l
    ) n
)
-- classification:fin
SELECT (SELECT count(*) FROM precision_candidate WHERE approximate) AS lieux_approximate,
       (SELECT count(*) FROM precision_candidate) AS lieux_total,
       count(DISTINCT a.id) AS activites_rattachees,
       count(DISTINCT a.id) FILTER (WHERE l.latitude = a.latitude AND l.longitude = a.longitude)
           AS activites_point_du_lieu,
       count(DISTINCT a.id) FILTER (WHERE l.latitude = a.latitude AND l.longitude = a.longitude
                                      AND a.status = 'PUBLISHED') AS activites_point_du_lieu_publiees
FROM activity a
JOIN schedule s ON s.activity_id = a.id
LEFT JOIN occurrence o ON o.schedule_id = s.id
JOIN precision_candidate p ON p.id = COALESCE(o.location_id, s.location_id)
JOIN location l ON l.id = p.id
WHERE p.approximate;

\echo '== 2. Lieux classés APPROXIMATE (100 premiers), pour relecture des faux positifs =='
WITH
-- classification:début
precision_candidate AS (
    SELECT n.id, n.name, n.address, n.city,
           n.t ~ '\m(divers|differents|plusieurs) lieux\M'
           OR (CASE WHEN n.c <> '' THEN btrim(regexp_replace(replace(n.t, n.c, ' '), '\s+', ' ', 'g')) ELSE n.t END)
              ~ '^(en )?(centre ville|centre village|centre du village|le village|dans le village|dans la ville|dans tout le village|dans les rues( du village| de la ville)?)$'
           AS approximate
    FROM (
        SELECT l.id, l.name, l.address, l.city,
               btrim(regexp_replace(translate(lower(coalesce(
                   CASE WHEN btrim(coalesce(l.address, '')) <> '' THEN l.address ELSE l.name END, '')),
                   'àáâãäåçèéêëìíîïñòóôõöùúûüýÿÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ',
                   'aaaaaaceeeeiiiinooooouuuuyyaaaaaaceeeeiiiinooooouuuuy'), '[^a-z0-9]+', ' ', 'g')) AS t,
               btrim(regexp_replace(translate(lower(coalesce(l.city, '')),
                   'àáâãäåçèéêëìíîïñòóôõöùúûüýÿÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ',
                   'aaaaaaceeeeiiiinooooouuuuyyaaaaaaceeeeiiiinooooouuuuy'), '[^a-z0-9]+', ' ', 'g')) AS c
        FROM location l
    ) n
)
-- classification:fin
SELECT id, name, address, city
FROM precision_candidate
WHERE approximate
ORDER BY city, name
LIMIT 100;

ROLLBACK;
