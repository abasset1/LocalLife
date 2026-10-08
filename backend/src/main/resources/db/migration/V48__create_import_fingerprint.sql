-- Import incrémental : empreinte du dernier contenu importé avec succès pour chaque élément d'une source.
--
-- Jusqu'ici chaque import réécrivait toutes les activités déjà connues et reconstruisait leur
-- programmation, leurs médias, liens, tags... même quand la source n'avait rien changé (5 à 7 minutes
-- pour 3 000 éléments, audit du 08/10/2026). ImportService compare désormais l'empreinte de l'élément
-- collecté à celle enregistrée ici et saute l'élément quand elles sont identiques.
--
-- * Une ligne par (source, clé d'import), écrite seulement quand l'activité ET son enrichissement ont
--   été enregistrés : un élément dont l'enrichissement a échoué est retenté à l'import suivant.
-- * content_hash : SHA-256 hexadécimal (ImportFingerprintService).
-- * Table vide au départ : le premier import après cette migration réécrit tout une dernière fois.

CREATE TABLE import_fingerprint (
    id           BIGSERIAL PRIMARY KEY,
    source_id    BIGINT       NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    import_key   VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_import_fingerprint UNIQUE (source_id, import_key)
);
