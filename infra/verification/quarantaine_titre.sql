-- Quarantaine : ce que le titre permettrait de classer — LECTURE SEULE.
-- Reprend les sections 5 à 7 de quarantaine_mesure.sql avec une comparaison titre / mots-clés rapide : titres et
-- mots-clés d'un seul mot sont rapprochés par jointure sur les mots, seuls les mots-clés de plusieurs mots sont
-- cherchés dans le titre entier.
-- Usage (VPS, depuis infra/) :
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/quarantaine_titre.sql > quarantaine-titre.txt 2>&1
-- Estimation : reproduit KeywordMatcher (suite de mots entière, casse ignorée, accents conservés) avec une découpe en
-- mots SQL approchée. Ordre de grandeur, pas une décision.

\set ON_ERROR_ROLLBACK on
\timing on
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '600s';
SET LOCAL lock_timeout = '5s';

\echo '== 5. Combien d''éléments le titre permettrait-il de classer ? (estimation) =='
\echo '   NEEDS_REVIEW : titre aujourd''hui ignoré, parce que la source fournit des mots-clés non reconnus.'
\echo '   UNCLASSIFIED : titre déjà essayé ; une correspondance ici signale un écart entre cette estimation et Java.'
WITH elements AS (
    SELECT cr.id, cr.status,
           btrim(regexp_replace(lower(coalesce(cr.title, '')), '[^[:alnum:]À-ÖØ-öø-ÿœŒ]+', ' ', 'g')) AS titre
    FROM classification_record cr
    WHERE cr.activity_id IS NULL
), mots_cles AS (
    SELECT DISTINCT c.code,
           btrim(regexp_replace(lower(k.keyword), '[^[:alnum:]À-ÖØ-öø-ÿœŒ]+', ' ', 'g')) AS motif
    FROM category_keyword k
    JOIN category c ON c.id = k.category_id
    WHERE k.active AND k.scope = 'ALL'
), mots_titres AS (
    SELECT DISTINCT e.id, mot
    FROM elements e, LATERAL regexp_split_to_table(e.titre, ' ') AS mot
    WHERE mot <> ''
), classes AS (
    SELECT mt.id, mc.code
    FROM mots_titres mt
    JOIN mots_cles mc ON mc.motif = mt.mot
    WHERE position(' ' IN mc.motif) = 0
    UNION
    SELECT e.id, mc.code
    FROM elements e
    JOIN mots_cles mc ON position(' ' IN mc.motif) > 0
                     AND strpos(' ' || e.titre || ' ', ' ' || mc.motif || ' ') > 0
)
SELECT e.status, count(*) AS elements, count(DISTINCT c.id) AS classables_par_le_titre
FROM elements e
LEFT JOIN classes c ON c.id = e.id
GROUP BY e.status
ORDER BY 1;

\echo '== 5 bis. Catégories que le titre donnerait aux éléments NEEDS_REVIEW =='
WITH elements AS (
    SELECT cr.id,
           btrim(regexp_replace(lower(coalesce(cr.title, '')), '[^[:alnum:]À-ÖØ-öø-ÿœŒ]+', ' ', 'g')) AS titre
    FROM classification_record cr
    WHERE cr.activity_id IS NULL AND cr.status = 'NEEDS_REVIEW'
), mots_cles AS (
    SELECT DISTINCT c.code,
           btrim(regexp_replace(lower(k.keyword), '[^[:alnum:]À-ÖØ-öø-ÿœŒ]+', ' ', 'g')) AS motif
    FROM category_keyword k
    JOIN category c ON c.id = k.category_id
    WHERE k.active AND k.scope = 'ALL'
), mots_titres AS (
    SELECT DISTINCT e.id, mot
    FROM elements e, LATERAL regexp_split_to_table(e.titre, ' ') AS mot
    WHERE mot <> ''
), classes AS (
    SELECT mt.id, mc.code
    FROM mots_titres mt
    JOIN mots_cles mc ON mc.motif = mt.mot
    WHERE position(' ' IN mc.motif) = 0
    UNION
    SELECT e.id, mc.code
    FROM elements e
    JOIN mots_cles mc ON position(' ' IN mc.motif) > 0
                     AND strpos(' ' || e.titre || ' ', ' ' || mc.motif || ' ') > 0
)
SELECT code AS categorie, count(DISTINCT id) AS elements
FROM classes
GROUP BY code
ORDER BY 2 DESC;

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
