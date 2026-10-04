-- CAT-006 (correctif) : la catégorie principale d'une activité devient
-- réellement obligatoire, au niveau base — pas au niveau service comme
-- le faisait la première version de ce ticket.
--
-- La version initiale de CAT-006 (V36__create_activity_classification_tables.sql,
-- table activity_primary_category) évitait volontairement une contrainte
-- NOT NULL directe sur `activity` au prétexte que les ~47 000 activités
-- déjà en base (audit CAT-000) n'avaient aucune classification. C'est
-- précisément le contournement à ne pas faire : si la donnée actuelle
-- pose problème au regard de la règle métier, c'est la donnée qui cède,
-- pas la règle.
--
-- En conséquence : les activités existantes, qui n'ont par construction
-- aucune classification (le référentiel CAT-001 n'existait pas quand
-- elles ont été importées/créées), sont supprimées ici plutôt que
-- migrées avec une valeur inventée. CASCADE supprime avec elles toutes
-- les données qui n'ont pas de sens sans leur activité (schedules,
-- occurrences, media, tags, accessibilité, metadata, liens, likes) —
-- `food_truck` n'est pas concerné (module indépendant, aucune FK vers
-- `activity`, voir CATEGORY_MIGRATION_MAP.md §2.5/§7).
TRUNCATE TABLE activity CASCADE;

-- La contrainte réelle, directement sur la table activity : une colonne
-- NOT NULL, pas une table séparée qui laisse croire que la relation est
-- facultative.
ALTER TABLE activity ADD COLUMN primary_category_id BIGINT NOT NULL REFERENCES category(id);

-- La table créée par V36 pour contourner cette même contrainte n'a plus
-- de raison d'être : elle n'a jamais contenu de données (table créée et
-- vidée dans la même série de migrations, jamais exposée par une API ni
-- utilisée en dehors de ActivityClassificationService, lui-même corrigé
-- dans le même correctif pour lire/écrire primary_category_id directement
-- sur `activity`).
DROP TABLE activity_primary_category;
