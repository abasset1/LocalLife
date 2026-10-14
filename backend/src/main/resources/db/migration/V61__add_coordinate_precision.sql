-- ADR-0011, phase A (§ 7.1, décisions du 14/10/2026) : précision géographique des coordonnées.
--
-- EXACT : position du lieu lui-même, posée par une validation humaine, jamais par un import.
-- APPROXIMATE : position estimée (zone, centre de commune, lieu multiple).
-- UNKNOWN : précision non établie ; valeur par défaut.
--
-- Rattrapage ciblé : seuls les lieux dont le texte (adresse, ou nom à défaut) est reconnu générique par la règle de
-- l'application (CoordinatePrecisionPolicy.isGenericPlaceText) passent APPROXIMATE ; tous les autres restent
-- UNKNOWN. Aucun lieu n'est classé EXACT. Le bloc « classification » est identique à celui de
-- infra/verification/phase_a_precision_mesure.sql (vérifié par GenericPlaceSqlParityIntegrationTest).
-- La copie sur l'activité ne vise que les activités dont le point affiché est celui d'un lieu approximatif rattaché.

ALTER TABLE location ADD COLUMN coordinate_precision VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    CONSTRAINT chk_location_coordinate_precision
    CHECK (coordinate_precision IN ('EXACT', 'APPROXIMATE', 'UNKNOWN'));

ALTER TABLE activity ADD COLUMN coordinate_precision VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
    CONSTRAINT chk_activity_coordinate_precision
    CHECK (coordinate_precision IN ('EXACT', 'APPROXIMATE', 'UNKNOWN'));

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
UPDATE location l SET coordinate_precision = 'APPROXIMATE'
FROM precision_candidate p
WHERE p.id = l.id AND p.approximate AND l.coordinate_precision = 'UNKNOWN';

UPDATE activity a SET coordinate_precision = 'APPROXIMATE'
WHERE a.coordinate_precision = 'UNKNOWN'
  AND EXISTS (
      SELECT 1
      FROM schedule s
      LEFT JOIN occurrence o ON o.schedule_id = s.id
      JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
      WHERE s.activity_id = a.id
        AND l.coordinate_precision = 'APPROXIMATE'
        AND l.latitude = a.latitude AND l.longitude = a.longitude);
