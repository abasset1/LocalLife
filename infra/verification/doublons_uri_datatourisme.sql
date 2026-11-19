-- Doublons DATAtourisme entre sources : même fiche (même URI) publiée par plusieurs sources.
-- LECTURE SEULE. Exemple : sources « DATAtourisme Occitanie » et « DATAtourisme Languedoc » sur le même fichier ;
-- un marché découpé par jours de la semaine dans l'une, continu (« annuel ») dans l'autre.
--
-- Usage : docker compose --env-file .env.beta -f docker-compose.beta.yml exec -T db \
--           psql -U locallife -d locallife -f - < infra/verification/doublons_uri_datatourisme.sql

-- 1) Nombre de fiches publiées en double, par paire de sources.
SELECT s1.name AS source_1, s1.status AS statut_1, s2.name AS source_2, s2.status AS statut_2,
       count(*) AS fiches_en_double
FROM import_fingerprint f1
JOIN activity a1 ON a1.source_id = f1.source_id AND a1.import_key = f1.import_key AND a1.status <> 'ARCHIVED'
JOIN import_fingerprint f2 ON f2.external_id = f1.external_id AND f2.source_id > f1.source_id
JOIN activity a2 ON a2.source_id = f2.source_id AND a2.import_key = f2.import_key AND a2.status <> 'ARCHIVED'
JOIN source s1 ON s1.id = f1.source_id
JOIN source s2 ON s2.id = f2.source_id
WHERE f1.external_id LIKE 'https://data.datatourisme.fr/%'
GROUP BY s1.name, s1.status, s2.name, s2.status
ORDER BY fiches_en_double DESC;

-- 2) Exemples : titre et date de début de chaque copie (30 premiers).
SELECT f1.external_id, a1.id AS id_1, a1.title AS titre_1, a1.start_date AS debut_1,
       a2.id AS id_2, a2.title AS titre_2, a2.start_date AS debut_2
FROM import_fingerprint f1
JOIN activity a1 ON a1.source_id = f1.source_id AND a1.import_key = f1.import_key AND a1.status <> 'ARCHIVED'
JOIN import_fingerprint f2 ON f2.external_id = f1.external_id AND f2.source_id > f1.source_id
JOIN activity a2 ON a2.source_id = f2.source_id AND a2.import_key = f2.import_key AND a2.status <> 'ARCHIVED'
WHERE f1.external_id LIKE 'https://data.datatourisme.fr/%'
ORDER BY a1.title
LIMIT 30;
