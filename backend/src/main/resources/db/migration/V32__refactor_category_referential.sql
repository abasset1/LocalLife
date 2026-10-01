-- CAT-001 (SPRINT_CATEG) : référentiel canonique des 9 catégories V1.
--
-- La table `category` créée par V5 est vide et orpheline (confirmé par
-- l'audit CAT-000, docs/CATEGORY_MIGRATION_MAP.md §1 : aucune FK depuis
-- `activity`, aucune donnée insérée, aucun code applicatif ne l'alimentait).
-- Elle est donc recréée ici avec le schéma recommandé par CAT-005
-- (séparation ID technique / CODE métier stable / LABEL traduit) plutôt que
-- migrée : il n'y a rien à conserver.
--
-- Le lien category_id sur `activity` (CAT-002, classification des activités
-- existantes) n'est volontairement PAS créé par cette migration : CAT-001
-- ne crée que le référentiel lui-même, pas son rattachement aux activités.

DROP TABLE IF EXISTS category;

CREATE TABLE category (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- Modèle de traduction recommandé par CAT-005 (docs/05_Sprints/SPRINT_CATEG.md) :
-- un label/une description par (catégorie, locale), jamais stockés dans la
-- table métier elle-même. V1 : une seule locale, fr-FR (les autres langues
-- pourront être ajoutées ultérieurement sans modification de schéma).
CREATE TABLE category_translation (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    locale      VARCHAR(10)  NOT NULL,
    label       VARCHAR(255) NOT NULL,
    description TEXT,
    UNIQUE (category_id, locale)
);

-- Les 9 catégories V1, dans l'ordre fixé par le sprint. Codes en
-- SCREAMING_SNAKE_CASE, indépendants de toute langue/source/label (règle
-- CAT-001). Aucune catégorie n'est créée à partir des valeurs sources
-- observées par CAT-000 (activity.category) : ce référentiel est canonique,
-- pas dérivé des imports.
INSERT INTO category (code) VALUES
    ('CULTURE'),
    ('GASTRONOMY'),
    ('MARKETS'),
    ('COMMERCE'),
    ('SPORT'),
    ('LEISURE'),
    ('NATURE'),
    ('TOURISM'),
    ('CITIZEN');

INSERT INTO category_translation (category_id, locale, label, description)
SELECT id, 'fr-FR', v.label, v.description
FROM category
JOIN (VALUES
    ('CULTURE',    'Culture',             'Spectacle vivant, musique, exposition, patrimoine, cinéma, conférence, visite.'),
    ('GASTRONOMY', 'Gastronomie',         'Dégustation, repas, foodtrucks, ateliers culinaires, animations gastronomiques.'),
    ('MARKETS',    'Marchés',             'Marchés de producteurs ou artisanaux, brocantes, vide-greniers, foires.'),
    ('COMMERCE',   'Commerce',            'Animations commerciales, braderies, nocturnes, ventes portées par des commerçants.'),
    ('SPORT',      'Sport',               'Compétitions, tournois, matchs, pratiques sportives encadrées.'),
    ('LEISURE',    'Loisirs',             'Activités de détente et de loisir, jeux, animations tous publics ou familiales.'),
    ('NATURE',     'Nature',              'Balades, sorties nature, jardins, activités de plein air non sportives.'),
    ('TOURISM',    'Tourisme',            'Découverte touristique du territoire, offices de tourisme, circuits.'),
    ('CITIZEN',    'Vie citoyenne',       'Vie associative, institutionnelle et civique : réunions publiques, forums, actions solidaires.')
) AS v(code, label, description) ON v.code = category.code;
