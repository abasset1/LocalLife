-- CAT-002 (SPRINT_CATEG) : référentiel canonique des 38 types d'activités V1.
--
-- Même schéma que le référentiel des catégories (CAT-001,
-- V32__refactor_category_referential.sql) : séparation ID technique /
-- CODE métier stable / LABEL traduit (règle du sprint). Un type n'est
-- jamais une catégorie (ex. FESTIVAL est un type, sa catégorie peut être
-- CULTURE) : cette table ne porte donc aucune colonne category_id — le
-- rattachement éventuel d'une activité à la fois à un type et à une
-- catégorie est une décision de classification (hors périmètre CAT-002).
--
-- Les 7 regroupements thématiques du sprint (culture/découverte,
-- transmission, marchés/commerce, gastronomie, sport/nature,
-- animation/citoyen, professionnel/institutionnel) ne sont pas modélisés
-- en base : ils ne sont pas demandés par les critères d'acceptation du
-- ticket (code stable, référencement complet, pas d'usage comme catégorie,
-- traductions) et servent ici uniquement de commentaire de lecture.

CREATE TABLE activity_type (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE activity_type_translation (
    id              BIGSERIAL PRIMARY KEY,
    activity_type_id BIGINT      NOT NULL REFERENCES activity_type(id) ON DELETE CASCADE,
    locale          VARCHAR(10)  NOT NULL,
    label           VARCHAR(255) NOT NULL,
    description     TEXT,
    UNIQUE (activity_type_id, locale)
);

INSERT INTO activity_type (code) VALUES
    -- Culture / découverte
    ('CONCERT'), ('SPECTACLE'), ('FESTIVAL'), ('EXPOSITION'),
    ('CONFERENCE'), ('RENCONTRE'), ('VISITE'), ('VISITE_GUIDEE'),
    -- Transmission
    ('ATELIER'), ('INITIATION'), ('FORMATION'), ('STAGE'),
    -- Marchés / commerce
    ('MARCHE'), ('MARCHE_PRODUCTEURS'), ('MARCHE_ARTISANAL'),
    ('MARCHE_SPECIALISE'), ('VIDE_GRENIER'), ('BROCANTE'), ('FOIRE'),
    -- Gastronomie
    ('FOODTRUCK'), ('DEGUSTATION'), ('REPAS'), ('ANIMATION_GASTRONOMIQUE'),
    -- Sport / nature
    ('COMPETITION'), ('TOURNOI'), ('MATCH'), ('RANDONNEE'), ('BALADE'),
    ('SORTIE_NATURE'),
    -- Animation / citoyen
    ('FETE'), ('ANIMATION'), ('JEU'), ('ACTION_COLLECTIVE'),
    ('ACTION_SOLIDAIRE'),
    -- Professionnel / institutionnel
    ('PORTES_OUVERTES'), ('SALON'), ('FORUM'), ('VENTE');

INSERT INTO activity_type_translation (activity_type_id, locale, label, description)
SELECT id, 'fr-FR', v.label, v.description
FROM activity_type
JOIN (VALUES
    ('CONCERT',                 'Concert',                 'Représentation musicale.'),
    ('SPECTACLE',               'Spectacle',               'Théâtre, danse, cirque, spectacle vivant en général.'),
    ('FESTIVAL',                'Festival',                 'Événement pluridisciplinaire ou thématique sur plusieurs temps forts.'),
    ('EXPOSITION',              'Exposition',               'Présentation d''œuvres ou de contenus à visiter librement.'),
    ('CONFERENCE',              'Conférence',               'Intervention ou débat devant un public.'),
    ('RENCONTRE',               'Rencontre',                'Échange avec un ou plusieurs intervenants (auteur, artiste...).'),
    ('VISITE',                  'Visite',                   'Visite libre d''un lieu.'),
    ('VISITE_GUIDEE',           'Visite guidée',            'Visite accompagnée par un guide.'),
    ('ATELIER',                 'Atelier',                  'Activité pratique encadrée, en petit groupe.'),
    ('INITIATION',              'Initiation',               'Première découverte encadrée d''une pratique.'),
    ('FORMATION',               'Formation',                'Transmission structurée de compétences.'),
    ('STAGE',                   'Stage',                    'Formation pratique sur plusieurs séances.'),
    ('MARCHE',                  'Marché',                   'Marché généraliste.'),
    ('MARCHE_PRODUCTEURS',      'Marché de producteurs',    'Marché de vente directe par les producteurs.'),
    ('MARCHE_ARTISANAL',        'Marché artisanal',         'Marché dédié à l''artisanat.'),
    ('MARCHE_SPECIALISE',       'Marché spécialisé',        'Marché thématique (antiquités, livres...).'),
    ('VIDE_GRENIER',            'Vide-grenier',             'Vente d''objets personnels par des particuliers.'),
    ('BROCANTE',                'Brocante',                 'Vente d''objets anciens ou d''occasion.'),
    ('FOIRE',                   'Foire',                    'Grand rassemblement commercial ou thématique.'),
    ('FOODTRUCK',               'Food truck',               'Rassemblement ou présence de camions de restauration.'),
    ('DEGUSTATION',             'Dégustation',               'Découverte gustative de produits.'),
    ('REPAS',                   'Repas',                     'Repas partagé ou organisé.'),
    ('ANIMATION_GASTRONOMIQUE', 'Animation gastronomique',  'Animation autour de la gastronomie.'),
    ('COMPETITION',             'Compétition',               'Épreuve sportive avec classement.'),
    ('TOURNOI',                 'Tournoi',                   'Compétition organisée en plusieurs phases.'),
    ('MATCH',                   'Match',                     'Rencontre sportive entre deux équipes ou adversaires.'),
    ('RANDONNEE',               'Randonnée',                 'Marche organisée en extérieur.'),
    ('BALADE',                  'Balade',                    'Sortie de découverte à faible intensité physique.'),
    ('SORTIE_NATURE',           'Sortie nature',             'Sortie encadrée de découverte de la nature.'),
    ('FETE',                    'Fête',                      'Événement festif local.'),
    ('ANIMATION',               'Animation',                 'Animation ponctuelle tous publics.'),
    ('JEU',                     'Jeu',                       'Activité ludique.'),
    ('ACTION_COLLECTIVE',       'Action collective',        'Action menée collectivement (nettoyage, bénévolat...).'),
    ('ACTION_SOLIDAIRE',        'Action solidaire',         'Action à vocation solidaire ou caritative.'),
    ('PORTES_OUVERTES',         'Portes ouvertes',          'Ouverture exceptionnelle d''un lieu habituellement fermé au public.'),
    ('SALON',                   'Salon',                     'Exposition professionnelle ou grand public organisée par secteur.'),
    ('FORUM',                   'Forum',                     'Rassemblement d''acteurs autour d''un thème (emploi, associations...).'),
    ('VENTE',                   'Vente',                     'Vente ponctuelle organisée.')
) AS v(code, label, description) ON v.code = activity_type.code;
