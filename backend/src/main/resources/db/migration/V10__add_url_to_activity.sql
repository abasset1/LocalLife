-- LL-6002 (Sprint 6) : reporte CollectedActivity#sourceUrl sur Activity.
-- Jusqu'ici collecté par OpenAgendaCollector puis perdu à la
-- normalisation, faute de colonne pour le porter (voir
-- docs/02_Architecture/DATA_QUALITY_AUDIT.md, LL-6001). Nullable :
-- toujours NULL pour une activité créée manuellement.
-- Même longueur que source.url (V8__create_source_table.sql), pour
-- rester cohérent avec l'autre champ URL du projet.
ALTER TABLE activity ADD COLUMN url VARCHAR(512);
