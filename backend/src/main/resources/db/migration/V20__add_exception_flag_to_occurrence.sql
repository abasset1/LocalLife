-- LL-11005 (Sprint 11) : rend une occurrence exceptionnelle identifiable
-- explicitement — voir docs/05_Sprints/SPRINT_11.md, section 8, et la
-- javadoc de Occurrence.java (« une exception est identifiable »).
-- NOT NULL DEFAULT FALSE : toute occurrence déjà en base (matérialisée
-- normalement par LL-11004) devient implicitement "normale" (critère
-- d'acceptation implicite de compatibilité avec les données existantes,
-- même principe que les migrations précédentes de ce sprint).

ALTER TABLE occurrence ADD COLUMN is_exception BOOLEAN NOT NULL DEFAULT FALSE;
