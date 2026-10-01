-- CAT-004 (SPRINT_CATEG) : référentiel canonique des 9 types
-- d'organisateurs V1.
--
-- Même schéma que CAT-001/CAT-002/CAT-003 : séparation ID technique /
-- CODE métier stable / LABEL traduit. Un organisateur n'est jamais une
-- catégorie (ex. ASSOCIATION ne signifie pas que l'activité appartient à
-- une catégorie "association") : cette table ne porte donc aucune colonne
-- category_id, activity_type_id ni audience_id.
--
-- Critère explicite du ticket, à distinguer des trois référentiels
-- précédents : "aucune logique métier ne dépend du label" — appliqué ici
-- en ne laissant aucune comparaison de chaîne sur organizer_type.label
-- nulle part dans le code (CategoryService/ActivityTypeService/
-- AudienceService s'en tiennent déjà au code pour la même raison, mais ce
-- ticket est le premier à l'énoncer explicitement comme critère).
--
-- Rappel (déjà noté en CAT-000) : organizer_type (qui organise l'activité,
-- dimension métier créée ici) est distinct de source.type (API/RSS/MANUAL,
-- canal technique d'import, table `source` existante) — à ne pas confondre.

CREATE TABLE organizer_type (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE organizer_type_translation (
    id               BIGSERIAL PRIMARY KEY,
    organizer_type_id BIGINT      NOT NULL REFERENCES organizer_type(id) ON DELETE CASCADE,
    locale           VARCHAR(10)  NOT NULL,
    label            VARCHAR(255) NOT NULL,
    description      TEXT,
    UNIQUE (organizer_type_id, locale)
);

INSERT INTO organizer_type (code) VALUES
    ('MUNICIPALITY'),
    ('ASSOCIATION'),
    ('COMPANY'),
    ('BUSINESS'),
    ('PROFESSIONAL'),
    ('INDIVIDUAL'),
    ('SPORTS_CLUB'),
    ('TOURISM_OFFICE'),
    ('INSTITUTION');

INSERT INTO organizer_type_translation (organizer_type_id, locale, label, description)
SELECT id, 'fr-FR', v.label, v.description
FROM organizer_type
JOIN (VALUES
    ('MUNICIPALITY',    'Municipalité',          'Commune ou intercommunalité.'),
    ('ASSOCIATION',     'Association',           'Association loi 1901 ou équivalent.'),
    ('COMPANY',          'Entreprise',            'Société, quelle que soit sa taille.'),
    ('BUSINESS',         'Commerce',              'Commerçant ou activité commerciale locale.'),
    ('PROFESSIONAL',     'Professionnel',         'Professionnel indépendant.'),
    ('INDIVIDUAL',       'Particulier',           'Personne physique, hors cadre professionnel.'),
    ('SPORTS_CLUB',      'Club sportif',          'Club ou association sportive.'),
    ('TOURISM_OFFICE',   'Office de tourisme',    'Office de tourisme ou organisme assimilé.'),
    ('INSTITUTION',      'Institution',           'Institution publique autre qu''une municipalité (État, région, département...).')
) AS v(code, label, description) ON v.code = organizer_type.code;
