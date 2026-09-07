-- LL-EF-008 (+ demande complémentaire d'Alex, voir ADR-0001) : jusqu'ici
-- seules latitude/longitude étaient stockées (voir javadoc historique de
-- ActivityController.CreateActivityRequest), ce qui empêchait d'afficher
-- une adresse lisible ou de regrouper les activités par ville dans la
-- nouvelle vue liste.
--
-- Colonnes nullables : les activités déjà en base (démo + contributions
-- manuelles + imports antérieurs à ce ticket) n'ont pas cette donnée et le
-- resteront tant qu'elles ne sont pas re-géocodées/ré-importées — pas de
-- backfill dans ce ticket (hors périmètre, voir ADR-0001).
ALTER TABLE activity ADD COLUMN address VARCHAR(500);
ALTER TABLE activity ADD COLUMN city VARCHAR(255);
ALTER TABLE activity ADD COLUMN postal_code VARCHAR(20);
