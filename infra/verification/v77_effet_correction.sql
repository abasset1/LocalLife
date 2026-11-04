-- Effet de la correction du filtre VIDE_GRENIERS_BROCANTES sur la source V77 (id 4733) — LECTURE SEULE.
-- Les 45 URI de l'export France du 04/11/2026 que le nouveau filtre ne collecte plus (39 hors sujet par le
-- type, 4 par le titre, 1 annulé, 1 position hors du territoire). Après déploiement, une activité publiée de
-- cette liste disparaît de la collecte et est archivée au prochain import complet (garde-fou de disparition
-- massive : au moins 10 et plus de 50 % des activités à venir de la source).
-- Usage (VPS) :
--   docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < verification/v77_effet_correction.sql > v77-effet.txt 2>&1
BEGIN TRANSACTION READ ONLY;
\echo '== Activités de la source V77 que le nouveau filtre ne collecterait plus (export du 04/11/2026) =='
WITH retires(uri) AS (VALUES
  ('https://data.datatourisme.fr/10/38ad7bf4-7bbf-3fac-90af-2a93f6a6890c'),
  ('https://data.datatourisme.fr/10/582f3e45-b119-375e-853a-84deec818366'),
  ('https://data.datatourisme.fr/10/7737ca8a-64fc-3315-98dc-a3c546da2d6f'),
  ('https://data.datatourisme.fr/10/e1ef496a-ff82-3608-a09c-8f9f22faf316'),
  ('https://data.datatourisme.fr/10/f4fa31c8-a6eb-3548-87d1-1927378fd1b4'),
  ('https://data.datatourisme.fr/10/f6f82013-221f-3725-8326-8d5b5a9ba28d'),
  ('https://data.datatourisme.fr/13/753f9210-dbc7-325d-b27d-1c7fcb098462'),
  ('https://data.datatourisme.fr/13/82ea0ab4-1dfe-322c-a398-c270cccdcc2e'),
  ('https://data.datatourisme.fr/13/f1f1da8c-b7a2-3c36-8b7f-4275d635bec7'),
  ('https://data.datatourisme.fr/15/5076f6bc-15da-3f5e-ad62-8608848f7011'),
  ('https://data.datatourisme.fr/15/dbc4ee3d-1893-3f1c-a413-229684f807fb'),
  ('https://data.datatourisme.fr/23/1c75ed97-452b-3943-85fb-470de9d1aa70'),
  ('https://data.datatourisme.fr/23/22443ce1-e66c-31b2-b41c-ce047aaeaf21'),
  ('https://data.datatourisme.fr/23/2a7eac67-940e-34ab-ab50-40b1fcbb4ad3'),
  ('https://data.datatourisme.fr/23/3db78d24-17a9-305f-84fd-8226a249826c'),
  ('https://data.datatourisme.fr/23/437408ab-3b43-348d-8472-81609df99e78'),
  ('https://data.datatourisme.fr/23/56d501cb-3609-3353-b851-9f5799fc71bf'),
  ('https://data.datatourisme.fr/23/81a2a12b-c25a-3b48-804a-eb83bcca5cf4'),
  ('https://data.datatourisme.fr/23/c1120bcb-9577-3e12-b59a-f4b7d3bd0100'),
  ('https://data.datatourisme.fr/23/d3d3cb20-8b8f-3467-8028-d2b272b8ec03'),
  ('https://data.datatourisme.fr/23/f52037a1-ca44-3b22-84a0-4723565ca416'),
  ('https://data.datatourisme.fr/26/de7cbdfb-4c21-3aea-b4b4-f27ec3c18e92'),
  ('https://data.datatourisme.fr/28/cc4f6258-4bce-32cf-984a-6e2b8257788f'),
  ('https://data.datatourisme.fr/28/cc8ac60f-c677-3ff9-99e6-5d0205022bb7'),
  ('https://data.datatourisme.fr/30/61a4423f-5e09-3894-9a5e-100a6665f91e'),
  ('https://data.datatourisme.fr/31/8ca9048c-1eca-3fd7-9e2a-9db7feb7e888'),
  ('https://data.datatourisme.fr/31/9689b6d2-e219-3959-889a-074f240eac08'),
  ('https://data.datatourisme.fr/32/1ec3f88b-0486-3ca8-b56e-bad994aeb752'),
  ('https://data.datatourisme.fr/36/1ee7f57d-cefe-3417-a8f6-339d933ccc69'),
  ('https://data.datatourisme.fr/36/28551f72-63f9-305a-abe5-3c0ed874b2ee'),
  ('https://data.datatourisme.fr/38/01f17a5c-6775-388e-8436-91da524f1cf1'),
  ('https://data.datatourisme.fr/38/0e7c1a72-bd20-3bfe-b632-b0bbf04280a9'),
  ('https://data.datatourisme.fr/38/48208bc3-172c-3267-8595-8892c893cb58'),
  ('https://data.datatourisme.fr/38/621ce4ad-ffd2-3dea-97d2-a7efc0373f53'),
  ('https://data.datatourisme.fr/38/7d0bf025-0693-389a-9a69-7f1128e5e70e'),
  ('https://data.datatourisme.fr/38/ab9e7daa-fc66-3b74-ad46-00e8dbe9fe1d'),
  ('https://data.datatourisme.fr/38/b1d18015-e9cf-34f1-b97d-47053cb1eae0'),
  ('https://data.datatourisme.fr/49/4fb3ea29-fdf9-35ea-b03a-f86f7021a078'),
  ('https://data.datatourisme.fr/5/3c608074-f54b-3e07-8b04-f238d07328dc'),
  ('https://data.datatourisme.fr/5/56dec28b-d261-398f-98e7-ccdf12468a2b'),
  ('https://data.datatourisme.fr/5/60dc09c7-8277-3152-8860-2efe0659ffe6'),
  ('https://data.datatourisme.fr/6/6f03d065-b55f-3690-ba7a-21650427258e'),
  ('https://data.datatourisme.fr/6/97d7f674-052e-316e-aee3-37f96b3d3020'),
  ('https://data.datatourisme.fr/6/bc12f522-ece5-3b74-8531-3aa8693869ab'),
  ('https://data.datatourisme.fr/6/c625993c-43d7-3f1f-9eb3-d9467894b1b4')
)
SELECT a.id, a.status, left(a.title, 60) AS titre, a.postal_code, a.start_date::date
FROM activity a JOIN retires r ON a.import_key = 'external:' || (SELECT name FROM source WHERE id = 4733) || ':' || r.uri
WHERE a.source_id = 4733 ORDER BY a.status, a.title;
\echo '== Total par statut (sur 45 URI retirées) =='
WITH retires(uri) AS (VALUES ('https://data.datatourisme.fr/10/38ad7bf4-7bbf-3fac-90af-2a93f6a6890c'), ('https://data.datatourisme.fr/10/582f3e45-b119-375e-853a-84deec818366'), ('https://data.datatourisme.fr/10/7737ca8a-64fc-3315-98dc-a3c546da2d6f'), ('https://data.datatourisme.fr/10/e1ef496a-ff82-3608-a09c-8f9f22faf316'), ('https://data.datatourisme.fr/10/f4fa31c8-a6eb-3548-87d1-1927378fd1b4'), ('https://data.datatourisme.fr/10/f6f82013-221f-3725-8326-8d5b5a9ba28d'), ('https://data.datatourisme.fr/13/753f9210-dbc7-325d-b27d-1c7fcb098462'), ('https://data.datatourisme.fr/13/82ea0ab4-1dfe-322c-a398-c270cccdcc2e'), ('https://data.datatourisme.fr/13/f1f1da8c-b7a2-3c36-8b7f-4275d635bec7'), ('https://data.datatourisme.fr/15/5076f6bc-15da-3f5e-ad62-8608848f7011'), ('https://data.datatourisme.fr/15/dbc4ee3d-1893-3f1c-a413-229684f807fb'), ('https://data.datatourisme.fr/23/1c75ed97-452b-3943-85fb-470de9d1aa70'), ('https://data.datatourisme.fr/23/22443ce1-e66c-31b2-b41c-ce047aaeaf21'), ('https://data.datatourisme.fr/23/2a7eac67-940e-34ab-ab50-40b1fcbb4ad3'), ('https://data.datatourisme.fr/23/3db78d24-17a9-305f-84fd-8226a249826c'), ('https://data.datatourisme.fr/23/437408ab-3b43-348d-8472-81609df99e78'), ('https://data.datatourisme.fr/23/56d501cb-3609-3353-b851-9f5799fc71bf'), ('https://data.datatourisme.fr/23/81a2a12b-c25a-3b48-804a-eb83bcca5cf4'), ('https://data.datatourisme.fr/23/c1120bcb-9577-3e12-b59a-f4b7d3bd0100'), ('https://data.datatourisme.fr/23/d3d3cb20-8b8f-3467-8028-d2b272b8ec03'), ('https://data.datatourisme.fr/23/f52037a1-ca44-3b22-84a0-4723565ca416'), ('https://data.datatourisme.fr/26/de7cbdfb-4c21-3aea-b4b4-f27ec3c18e92'), ('https://data.datatourisme.fr/28/cc4f6258-4bce-32cf-984a-6e2b8257788f'), ('https://data.datatourisme.fr/28/cc8ac60f-c677-3ff9-99e6-5d0205022bb7'), ('https://data.datatourisme.fr/30/61a4423f-5e09-3894-9a5e-100a6665f91e'), ('https://data.datatourisme.fr/31/8ca9048c-1eca-3fd7-9e2a-9db7feb7e888'), ('https://data.datatourisme.fr/31/9689b6d2-e219-3959-889a-074f240eac08'), ('https://data.datatourisme.fr/32/1ec3f88b-0486-3ca8-b56e-bad994aeb752'), ('https://data.datatourisme.fr/36/1ee7f57d-cefe-3417-a8f6-339d933ccc69'), ('https://data.datatourisme.fr/36/28551f72-63f9-305a-abe5-3c0ed874b2ee'), ('https://data.datatourisme.fr/38/01f17a5c-6775-388e-8436-91da524f1cf1'), ('https://data.datatourisme.fr/38/0e7c1a72-bd20-3bfe-b632-b0bbf04280a9'), ('https://data.datatourisme.fr/38/48208bc3-172c-3267-8595-8892c893cb58'), ('https://data.datatourisme.fr/38/621ce4ad-ffd2-3dea-97d2-a7efc0373f53'), ('https://data.datatourisme.fr/38/7d0bf025-0693-389a-9a69-7f1128e5e70e'), ('https://data.datatourisme.fr/38/ab9e7daa-fc66-3b74-ad46-00e8dbe9fe1d'), ('https://data.datatourisme.fr/38/b1d18015-e9cf-34f1-b97d-47053cb1eae0'), ('https://data.datatourisme.fr/49/4fb3ea29-fdf9-35ea-b03a-f86f7021a078'), ('https://data.datatourisme.fr/5/3c608074-f54b-3e07-8b04-f238d07328dc'), ('https://data.datatourisme.fr/5/56dec28b-d261-398f-98e7-ccdf12468a2b'), ('https://data.datatourisme.fr/5/60dc09c7-8277-3152-8860-2efe0659ffe6'), ('https://data.datatourisme.fr/6/6f03d065-b55f-3690-ba7a-21650427258e'), ('https://data.datatourisme.fr/6/97d7f674-052e-316e-aee3-37f96b3d3020'), ('https://data.datatourisme.fr/6/bc12f522-ece5-3b74-8531-3aa8693869ab'), ('https://data.datatourisme.fr/6/c625993c-43d7-3f1f-9eb3-d9467894b1b4'))
SELECT a.status, count(*) FROM activity a JOIN retires r ON a.import_key = 'external:' || (SELECT name FROM source WHERE id = 4733) || ':' || r.uri WHERE a.source_id = 4733 GROUP BY a.status;
ROLLBACK;
