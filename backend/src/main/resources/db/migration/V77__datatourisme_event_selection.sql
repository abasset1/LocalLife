-- Sélection d'événements d'une source DATAtourisme, et source « vide-greniers et brocantes » pour la France.
--
-- Mesure (29/10/2026) : l'export DATAtourisme France compte 1 052 vide-greniers, brocantes, puces, braderies et
-- bourses à venir dans 68 départements ; la bêta en publie 138, dans 32 départements. Importer tout l'export
-- doublerait les événements déjà publiés par OpenAgenda (les offices de tourisme publient sur les deux, et le
-- rapprochement entre sources n'est pas branché) : la nouvelle source ne garde que cette niche.
--
-- event_selection : nom d'une sélection de DataTourismeCollector (DataTourismeEventSelection) ; NULL = tous les
-- événements du fichier (comportement des sources existantes, inchangé). Colonne hors du modèle Source : lue par
-- DataTourismeCollectorFactory, préservée par les mises à jour de l'administration (seules les colonnes du modèle
-- sont écrites).
ALTER TABLE source ADD COLUMN event_selection VARCHAR(40);
ALTER TABLE source ADD CONSTRAINT chk_source_event_selection
    CHECK (event_selection IS NULL OR (type = 'DATATOURISME' AND event_selection IN ('VIDE_GRENIERS_BROCANTES')));

-- Source créée INACTIVE : comme toute source DATAtourisme, elle s'active après un essai à blanc réussi
-- (Administration → Imports → « Activer après vérification »). Fichier France (datatourisme-fma.csv), sans filtre
-- de département.
INSERT INTO source (name, type, url, status, last_sync_at, agenda_uid, region_filter, event_selection)
VALUES (
    'DATAtourisme France — vide-greniers et brocantes (data.gouv.fr)',
    'DATATOURISME',
    'https://www.data.gouv.fr/api/1/datasets/r/2c6023f2-ffce-4c1a-9728-bdc020e463b5',
    'INACTIVE',
    NULL,
    NULL,
    NULL,
    'VIDE_GRENIERS_BROCANTES');

-- Catégorie par défaut : Marchés, pour les événements que les mots-clés ne classent pas (« Bourse aux jouets »,
-- « Marché aux puces ») ; « vide-grenier » et « brocante » sont déjà des mots-clés de Marchés.
INSERT INTO source_default_category (source_id, category_id)
SELECT s.id, c.id
FROM source s
JOIN category c ON c.code = 'MARKETS'
WHERE s.name = 'DATAtourisme France — vide-greniers et brocantes (data.gouv.fr)';
