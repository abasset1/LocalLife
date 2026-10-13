-- Source DATAtourisme via data.gouv.fr (voir docs/02_Architecture/DATAGOUV_SOURCES.md
-- et DataTourismeCollector) : événements de l'export CSV quotidien publié par
-- ADN Tourisme, sans clé API.
--
-- url : URL stable de la ressource data.gouv.fr « datatourisme-reg-pac.csv »
-- (Provence-Alpes-Côte d'Azur), qui redirige vers le fichier du jour.
-- region_filter : départements retenus (préfixes de code postal séparés par des
-- virgules) — Vaucluse seul, zone de la bêta. Modifiable depuis l'interface
-- d'administration (ex. « 84,13 », ou vide pour toute la région ; ou une autre
-- ressource, ex. datatourisme-fma.csv 2c6023f2-ffce-4c1a-9728-bdc020e463b5 pour
-- la France entière).
INSERT INTO source (name, type, url, status, last_sync_at, agenda_uid, region_filter)
VALUES (
    'DATAtourisme PACA (data.gouv.fr)',
    'DATATOURISME',
    'https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80',
    'ACTIVE',
    NULL,
    NULL,
    '84');
