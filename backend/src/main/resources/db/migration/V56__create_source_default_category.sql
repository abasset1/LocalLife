-- Catégories par défaut d'une source : appliquées à un élément importé quand ni ses mots-clés source ni
-- son titre ne donnent de catégorie, au lieu de le laisser en quarantaine.
--
-- Constat du 09/10/2026 sur 200 agendas officiels OpenAgenda : 9 463 éléments en quarantaine sur 16 780,
-- concentrés sur quelques agendas thématiques dont les titres sont des noms d'artistes ou d'œuvres, sans
-- mot de domaine (un seul agenda de billetterie en comptait 4 521). Aucun mot-clé ne peut les classer,
-- alors que la nature de l'agenda suffit à dire leur catégorie.
--
-- * 0..N catégories par source (modèle 1..N des activités, V42). Aucune ligne = comportement inchangé.
-- * Réglées par un administrateur, jamais déduites automatiquement.
-- * Les mots-clés gardent la priorité : la valeur par défaut n'intervient qu'en dernier recours.

CREATE TABLE source_default_category (
    source_id   BIGINT NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    PRIMARY KEY (source_id, category_id)
);
