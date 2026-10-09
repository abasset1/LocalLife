-- Déduplication entre sources : identifiant de l'élément chez son fournisseur (pour OpenAgenda, le slug
-- de l'événement), indépendant de la source qui l'a importé.
--
-- Un même événement OpenAgenda peut être partagé par plusieurs agendas : il porte alors le même slug
-- dans chacun. Sur l'import du 09/10/2026 (50 agendas officiels, 2 425 activités publiées), 25 des 29
-- doublons entre sources étaient dans ce cas, et aucune paire d'activités de même slug ne désignait
-- deux événements différents. La clé d'import, elle, contient le nom de la source et ne permet pas de
-- les rapprocher.
--
-- ImportService s'en sert pour ne pas créer une activité déjà importée par une autre source
-- (ImportFingerprintService).
--
-- Reprise des lignes existantes : la clé d'import vaut « external:<nom de la source>:<slug> ». Le nom
-- de la source peut contenir « : », pas le slug, d'où la suppression jusqu'au dernier « : ». Les clés
-- composites (« composite:<hash> », sans identifiant externe) restent à NULL.

ALTER TABLE import_fingerprint ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);

UPDATE import_fingerprint
SET external_id = regexp_replace(import_key, '^external:.*:', '')
WHERE external_id IS NULL
  AND import_key LIKE 'external:%';

CREATE INDEX IF NOT EXISTS idx_import_fingerprint_external_id ON import_fingerprint (external_id);
