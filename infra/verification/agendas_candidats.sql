-- Agendas candidats à une catégorie par défaut — LECTURE SEULE.
-- Usage (VPS, depuis infra/) :
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/agendas_candidats.sql > agendas-candidats.txt 2>&1
-- Quarantaine ACTUELLE seulement : un élément encore présent dans sa source a été re-décidé à l'import qui a suivi
-- le changement de règles du 24/10/2026 (DECISION_RULES_VERSION), donc mis à jour depuis ; un élément plus ancien
-- a disparu de sa source. Paramètre : -v depuis="'2026-10-24 12:00'" pour changer la date de coupure.
-- Pour chaque agenda sans catégorie par défaut, du plus chargé au moins chargé (120 premiers) :
--   categorie_dominante / part_dominante : catégorie la plus fréquente parmi ses activités publiées, et sa part.
--   Une part proche de 100 % sur assez d'activités désigne un agenda thématique : sa catégorie par défaut est sûre.
--   exemples : 4 titres en quarantaine, pour vérifier à l'œil.

\set ON_ERROR_ROLLBACK on
\if :{?depuis}
\else
\set depuis '''2026-10-24 12:00'''
\endif
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '300s';
SET LOCAL lock_timeout = '5s';

\echo '== 1. Quarantaine actuelle (éléments re-décidés depuis la date de coupure) =='
SELECT count(*) AS quarantaine_actuelle,
       count(*) FILTER (WHERE status = 'NEEDS_REVIEW') AS mots_cles_non_reconnus,
       count(*) FILTER (WHERE status = 'UNCLASSIFIED') AS sans_mot_cle,
       count(DISTINCT source_id) AS agendas
FROM classification_record
WHERE activity_id IS NULL AND updated_at >= :depuis;

\echo '== 2. Les 120 agendas sans catégorie par défaut les plus chargés =='
WITH q AS (
    SELECT source_id, count(*) AS quarantaine
    FROM classification_record
    WHERE activity_id IS NULL AND updated_at >= :depuis
      AND source_id NOT IN (SELECT source_id FROM source_default_category)
    GROUP BY source_id
), rang AS (
    SELECT q.*, row_number() OVER (ORDER BY quarantaine DESC, source_id) AS rang FROM q
), publiees AS (
    SELECT a.source_id, c.code, count(DISTINCT a.id) AS n
    FROM activity a
    JOIN activity_category ac ON ac.activity_id = a.id
    JOIN category c ON c.id = ac.category_id
    WHERE a.status = 'PUBLISHED' AND a.source_id IN (SELECT source_id FROM rang WHERE rang <= 120)
    GROUP BY a.source_id, c.code
), totaux AS (
    SELECT source_id, count(*) AS publiees
    FROM activity
    WHERE status = 'PUBLISHED' AND source_id IN (SELECT source_id FROM rang WHERE rang <= 120)
    GROUP BY source_id
), dominante AS (
    SELECT DISTINCT ON (source_id) source_id, code, n FROM publiees ORDER BY source_id, n DESC, code
)
SELECT r.rang, r.source_id, left(s.name, 45) AS agenda, r.quarantaine,
       coalesce(t.publiees, 0) AS publiees,
       coalesce(d.code, '-') AS categorie_dominante,
       CASE WHEN t.publiees > 0 THEN round(100.0 * d.n / t.publiees)::int END AS part_dominante,
       (SELECT string_agg(left(x.title, 32), ' | ')
          FROM (SELECT cr.title FROM classification_record cr
                WHERE cr.source_id = r.source_id AND cr.activity_id IS NULL AND cr.updated_at >= :depuis
                ORDER BY md5(cr.import_key) LIMIT 4) x) AS exemples
FROM rang r
JOIN source s ON s.id = r.source_id
LEFT JOIN totaux t ON t.source_id = r.source_id
LEFT JOIN dominante d ON d.source_id = r.source_id
WHERE r.rang <= 120
ORDER BY r.rang;

\echo '== 3. Agendas qui ressemblent à des annuaires (aucun événement publié, noms « catalogue », « structures »...) =='
SELECT s.id, left(s.name, 70) AS agenda, s.status, count(cr.id) AS quarantaine
FROM source s
LEFT JOIN classification_record cr ON cr.source_id = s.id AND cr.activity_id IS NULL AND cr.updated_at >= :depuis
WHERE s.name ~* '(catalogue|structures d.accueil|annuaire|h[ée]bergement)'
GROUP BY s.id, s.name, s.status
ORDER BY 4 DESC
LIMIT 40;

ROLLBACK;
