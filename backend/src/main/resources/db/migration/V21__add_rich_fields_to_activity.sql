-- LL-11006 (Sprint 11, section 9) : permet de construire une fiche
-- événementielle complète — voir la javadoc de Activity.java pour le
-- détail de chaque champ et le choix d'une conception indépendante
-- d'OpenAgenda (critère d'acceptation explicite du ticket).
-- Tous nullables, même principe que address/city/postalCode
-- (V15__add_address_to_activity.sql) : aucune activité existante n'est
-- affectée par cette migration (compatibilité avec les données
-- existantes), aucune n'est rejetée pour l'absence de ces informations.

ALTER TABLE activity ADD COLUMN long_description TEXT;
ALTER TABLE activity ADD COLUMN conditions TEXT;
ALTER TABLE activity ADD COLUMN age_min INTEGER;
ALTER TABLE activity ADD COLUMN age_max INTEGER;
