-- Détail des doublons exacts signalés par controle_beta.sql (section 3f) — LECTURE SEULE.
-- Usage (VPS, depuis infra/) :
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/doublons_detail.sql > doublons-detail.txt 2>&1
-- Question posée : un groupe « même source, même titre, même jour » contient-il des événements distincts à la
-- source (clés d'import, horaires ou lieux différents) ou la même chose importée plusieurs fois ?
-- L'index unique (source_id, import_key) exclut deux fois la même clé : des doublons viennent donc de clés
-- différentes, soit fournies ainsi par la source, soit recalculées différemment d'un import à l'autre.

\set ON_ERROR_ROLLBACK on
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '120s';
SET LOCAL lock_timeout = '5s';

\echo '== 1. Ampleur : activités publiées en double exact, par source (20 premières) =='
WITH groupes AS (
    SELECT a.source_id, lower(btrim(a.title)) AS titre, a.start_date::date AS debut, count(*) AS nb
    FROM activity a
    WHERE a.status = 'PUBLISHED'
    GROUP BY 1, 2, 3
    HAVING count(*) > 1
)
SELECT g.source_id, s.name AS source, s.type, count(*) AS groupes, sum(g.nb) AS activites,
       sum(g.nb - 1) AS en_trop_si_vrais_doublons
FROM groupes g
JOIN source s ON s.id = g.source_id
GROUP BY g.source_id, s.name, s.type
ORDER BY 6 DESC
LIMIT 20;

\echo '== 2. Total des activités en trop si ce sont de vrais doublons =='
SELECT sum(nb - 1) AS en_trop, count(*) AS groupes
FROM (SELECT count(*) AS nb FROM activity WHERE status = 'PUBLISHED'
      GROUP BY source_id, lower(btrim(title)), start_date::date HAVING count(*) > 1) g;

\echo '== 3. Nature des 20 plus gros groupes : horaires, lieux et adresses distincts =='
\echo '   horaires_distincts = 1 et lieux_distincts = 1 : vraisemblablement le même événement plusieurs fois.'
\echo '   Plusieurs horaires ou lieux : créneaux ou lieux différents, publiés sous le même titre par la source.'
WITH groupes AS (
    SELECT a.source_id, lower(btrim(a.title)) AS titre, a.start_date::date AS debut
    FROM activity a
    WHERE a.status = 'PUBLISHED'
    GROUP BY 1, 2, 3
    HAVING count(*) > 1
    ORDER BY count(*) DESC
    LIMIT 20
)
SELECT g.source_id, left(g.titre, 60) AS titre, g.debut, count(DISTINCT a.id) AS nb,
       count(DISTINCT a.import_key) AS cles_import,
       count(DISTINCT o.start_at) AS horaires_distincts,
       count(DISTINCT coalesce(o.location_id, sc.location_id)) AS lieux_distincts,
       count(DISTINCT (a.latitude, a.longitude)) AS positions_distinctes,
       count(DISTINCT coalesce(a.url, '')) AS urls_distinctes,
       min(a.created_at)::date AS premiere_creation, max(a.created_at)::date AS derniere_creation
FROM groupes g
JOIN activity a ON a.source_id = g.source_id AND lower(btrim(a.title)) = g.titre AND a.start_date::date = g.debut
                AND a.status = 'PUBLISHED'
LEFT JOIN schedule sc ON sc.activity_id = a.id
LEFT JOIN occurrence o ON o.schedule_id = sc.id
GROUP BY g.source_id, g.titre, g.debut
ORDER BY nb DESC;

\echo '== 4. Exemple : 10 activités du plus gros groupe (clé d''import, horaire, lieu, adresse) =='
WITH g AS (
    SELECT source_id, lower(btrim(title)) AS titre, start_date::date AS debut
    FROM activity
    WHERE status = 'PUBLISHED'
    GROUP BY 1, 2, 3
    ORDER BY count(*) DESC
    LIMIT 1
)
SELECT a.id, a.import_key, a.start_date, a.end_date, a.address, a.city, a.created_at::date AS cree_le,
       left(a.url, 80) AS url
FROM activity a
JOIN g ON a.source_id = g.source_id AND lower(btrim(a.title)) = g.titre AND a.start_date::date = g.debut
WHERE a.status = 'PUBLISHED'
ORDER BY a.id
LIMIT 10;

ROLLBACK;
