-- Mesure de la quarantaine de classification — LECTURE SEULE.
-- Usage (VPS, depuis infra/) :
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/quarantaine_mesure.sql > quarantaine-mesure.txt 2>&1
-- Contexte : chaque import compte environ 37 700 éléments « à relire » sur 101 700 récupérés (24/10/2026). Ils ne
-- sont pas publiés : aucune catégorie n'a pu leur être attribuée (ClassificationService, ActivityClassifier).
-- Deux cas, distingués par classification_record.status :
--   NEEDS_REVIEW : la source fournit des mots-clés (catégorie d'origine ou tags), aucun n'est dans le référentiel.
--                  Le titre n'est alors PAS essayé (les mots-clés source ont priorité).
--   UNCLASSIFIED : la source ne fournit aucun mot-clé, et le titre ne contient aucun mot-clé du référentiel.
-- Questions : quelles sources, quels mots-clés source reviennent le plus, et combien d'éléments le titre
-- aurait-il permis de classer ?
-- Estimation : la correspondance des titres reproduit KeywordMatcher (suite de mots entière, casse ignorée, accents
-- conservés) avec une découpe en mots SQL approchée. Ordre de grandeur, pas une décision.
-- Aucune donnée personnelle : titres et mots-clés d'événements publics.

\set ON_ERROR_ROLLBACK on
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '300s';
SET LOCAL lock_timeout = '5s';

\echo '== 1. Stock en quarantaine (sans activité) par statut et raison =='
SELECT status, coalesce(reason, '-') AS raison, count(*) AS elements,
       count(DISTINCT source_id) AS sources, max(updated_at)::date AS derniere_decision
FROM classification_record
WHERE activity_id IS NULL
GROUP BY status, reason
ORDER BY 3 DESC;

\echo '== 2. Les 30 sources les plus concernées =='
\echo '   publiees : activités publiées de la source ; part_quarantaine : quarantaine / (quarantaine + publiées).'
\echo '   defaut : nombre de catégories par défaut réglées pour la source (0 = aucune).'
WITH q AS (
    SELECT source_id, count(*) AS quarantaine,
           count(*) FILTER (WHERE status = 'NEEDS_REVIEW') AS mots_cles_non_reconnus,
           count(*) FILTER (WHERE status = 'UNCLASSIFIED') AS sans_mot_cle
    FROM classification_record
    WHERE activity_id IS NULL
    GROUP BY source_id
), p AS (
    SELECT source_id, count(*) AS publiees FROM activity WHERE status = 'PUBLISHED' GROUP BY source_id
)
SELECT q.source_id, left(s.name, 50) AS source, q.quarantaine, q.mots_cles_non_reconnus, q.sans_mot_cle,
       coalesce(p.publiees, 0) AS publiees,
       round(100.0 * q.quarantaine / (q.quarantaine + coalesce(p.publiees, 0)))::int AS part_quarantaine,
       (SELECT count(*) FROM source_default_category d WHERE d.source_id = q.source_id) AS defaut
FROM q
JOIN source s ON s.id = q.source_id
LEFT JOIN p ON p.source_id = q.source_id
ORDER BY q.quarantaine DESC
LIMIT 30;

\echo '== 3. Concentration : part du stock portée par les 10, 50 et 200 premières sources =='
WITH q AS (
    SELECT source_id, count(*) AS n, row_number() OVER (ORDER BY count(*) DESC) AS rang
    FROM classification_record
    WHERE activity_id IS NULL
    GROUP BY source_id
)
SELECT sum(n) AS total,
       round(100.0 * sum(n) FILTER (WHERE rang <= 10) / sum(n))::int AS pct_10_sources,
       round(100.0 * sum(n) FILTER (WHERE rang <= 50) / sum(n))::int AS pct_50_sources,
       round(100.0 * sum(n) FILTER (WHERE rang <= 200) / sum(n))::int AS pct_200_sources,
       count(*) AS sources
FROM q;

\echo '== 4. Les 100 mots-clés source non reconnus les plus fréquents (éléments NEEDS_REVIEW) =='
\echo '   Candidats à ajouter au référentiel (category_keyword) : un mot-clé ajouté classe tous ses éléments.'
WITH signaux AS (
    SELECT cr.id, cr.source_id, btrim(lower(signal)) AS mot_cle
    FROM classification_record cr,
         LATERAL regexp_split_to_table(
             concat_ws(',', nullif(btrim(cr.source_category), ''), nullif(btrim(cr.source_tags), '')), ',') AS signal
    WHERE cr.activity_id IS NULL AND cr.status = 'NEEDS_REVIEW'
)
SELECT mot_cle, count(DISTINCT id) AS elements, count(DISTINCT source_id) AS sources
FROM signaux
WHERE mot_cle <> ''
GROUP BY mot_cle
ORDER BY 2 DESC
LIMIT 100;

\echo '== 5. Combien d''éléments le titre aurait-il permis de classer ? (estimation) =='
\echo '   NEEDS_REVIEW : titre aujourd''hui ignoré, parce que la source fournit des mots-clés non reconnus.'
\echo '   UNCLASSIFIED : titre déjà essayé ; une correspondance ici signale un écart entre cette estimation et Java.'
WITH mots AS (
    SELECT ' ' || btrim(regexp_replace(lower(keyword), '[^[:alnum:]À-ÖØ-öø-ÿœŒ]+', ' ', 'g')) || ' ' AS motif
    FROM category_keyword
    WHERE active AND scope = 'ALL'
), elements AS (
    SELECT cr.id, cr.status,
           ' ' || btrim(regexp_replace(lower(coalesce(cr.title, '')), '[^[:alnum:]À-ÖØ-öø-ÿœŒ]+', ' ', 'g')) || ' '
               AS titre
    FROM classification_record cr
    WHERE cr.activity_id IS NULL
)
SELECT e.status, count(*) AS elements,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM mots m WHERE m.motif <> '  ' AND strpos(e.titre, m.motif) > 0))
           AS classables_par_le_titre
FROM elements e
GROUP BY e.status
ORDER BY 1;

\echo '== 6. Mots les plus fréquents des titres en quarantaine (100 premiers, 4 lettres et plus) =='
\echo '   Pistes de mots-clés de titre (portée ALL) ; les mots trop généraux (« atelier », « visite ») sont à écarter.'
WITH mots_titre AS (
    SELECT cr.id, mot
    FROM classification_record cr,
         LATERAL regexp_split_to_table(
             regexp_replace(lower(coalesce(cr.title, '')), '[^[:alnum:]À-ÖØ-öø-ÿœŒ]+', ' ', 'g'), ' ') AS mot
    WHERE cr.activity_id IS NULL
)
SELECT mot, count(DISTINCT id) AS elements
FROM mots_titre
WHERE length(mot) >= 4
  AND mot NOT IN ('pour', 'avec', 'dans', 'sur', 'les', 'des', 'une', 'aux', 'vous', 'votre', 'vos', 'nous', 'notre',
                  'plus', 'tout', 'tous', 'toute', 'toutes', 'entre', 'autour', 'chez', 'sans', 'leur', 'leurs',
                  'cette', 'quoi', 'comment', '2025', '2026', '2027')
GROUP BY mot
ORDER BY 2 DESC
LIMIT 100;

\echo '== 7. Exemples : 30 éléments NEEDS_REVIEW des 5 sources les plus concernées =='
WITH top AS (
    SELECT source_id FROM classification_record
    WHERE activity_id IS NULL AND status = 'NEEDS_REVIEW'
    GROUP BY source_id ORDER BY count(*) DESC LIMIT 5
), ex AS (
    SELECT cr.source_id, left(cr.title, 70) AS titre, left(cr.source_category, 40) AS categorie_source,
           left(cr.source_tags, 80) AS tags_source,
           row_number() OVER (PARTITION BY cr.source_id ORDER BY cr.id) AS n
    FROM classification_record cr
    JOIN top USING (source_id)
    WHERE cr.activity_id IS NULL AND cr.status = 'NEEDS_REVIEW'
)
SELECT source_id, titre, categorie_source, tags_source FROM ex WHERE n <= 6 ORDER BY source_id, n;

ROLLBACK;
