-- Réparation du référentiel : les mots-clés de V39, V40 et V41 (« théâtre », « concert », « exposition »,
-- « architecture », « musée », « danse », « lecture »...) peuvent être absents d'une base dont la table
-- category_keyword a été vidée après leur installation : ces migrations ne sont jamais rejouées par
-- Flyway, alors que V42 à V45 l'ont été ensuite. Constaté sur l'import du 08/10/2026 : 61 mots-clés
-- manquants, 36 % des éléments collectés classés au lieu de 59 % (1 937 éléments en quarantaine sur 3 049).
--
-- Cette migration remet le référentiel dans l'état attendu après V45. Sans effet sur une base saine :
-- insertions ON CONFLICT DO NOTHING, aucun mot-clé existant n'est modifié.
--
-- Les éléments en quarantaine n'ont pas d'activité : ils sont re-décidés au prochain import, sans reprise
-- de données.

-- Mots-clés actifs de V39 à V41.
INSERT INTO category_keyword (category_id, keyword)
SELECT c.id, v.keyword
FROM category c
JOIN (
    VALUES
        -- V39
        ('CULTURE',    'théâtre'),
        ('CULTURE',    'exposition'),
        ('CULTURE',    'concert'),
        ('GASTRONOMY', 'dégustation'),
        ('MARKETS',    'marché'),
        ('MARKETS',    'vide-grenier'),
        ('MARKETS',    'brocante'),
        ('COMMERCE',   'braderie'),
        ('SPORT',      'football'),
        ('SPORT',      'rugby'),
        ('NATURE',     'sortie nature'),
        ('LEISURE',    'jeux de société'),
        ('CITIZEN',    'réunion publique'),
        -- V40
        ('CULTURE',    'architecture'),
        ('CULTURE',    'architecte'),
        ('CULTURE',    'architectes'),
        ('CULTURE',    'architecturale'),
        ('CULTURE',    'architecturales'),
        ('CULTURE',    'architectural'),
        ('CULTURE',    'architecturaux'),
        ('CULTURE',    'architecture contemporaine'),
        ('CULTURE',    'musée'),
        ('CULTURE',    'musées'),
        ('CULTURE',    'photographie'),
        ('CULTURE',    'photographies'),
        ('CULTURE',    'photo'),
        ('CULTURE',    'dessin'),
        ('CULTURE',    'dessins'),
        ('CULTURE',    'peinture'),
        ('CULTURE',    'peintre'),
        ('CULTURE',    'lecture'),
        ('CULTURE',    'lectures'),
        ('CULTURE',    'livre'),
        ('CULTURE',    'livres'),
        ('CULTURE',    'roman'),
        ('CULTURE',    'romans'),
        ('CULTURE',    'poésie'),
        ('CULTURE',    'littérature'),
        ('CULTURE',    'danse'),
        ('CULTURE',    'chanson'),
        ('CULTURE',    'chant'),
        ('CULTURE',    'cirque'),
        ('CULTURE',    'cabaret'),
        ('CULTURE',    'vernissage'),
        ('LEISURE',    'escape game'),
        ('LEISURE',    'quizz'),
        ('LEISURE',    'jeu'),
        ('LEISURE',    'jeux'),
        ('LEISURE',    'jeu de piste'),
        ('LEISURE',    'jeu de plateau'),
        ('LEISURE',    'kermesse'),
        ('LEISURE',    'fête foraine'),
        ('GASTRONOMY', 'couscous'),
        ('GASTRONOMY', 'muscat'),
        -- V41
        ('NATURE',     'biodiversité'),
        ('SPORT',      'vélo'),
        ('SPORT',      'randonnée'),
        ('SPORT',      'randonnées')
) AS v(category_code, keyword)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;

-- Mots-clés de V40 désactivés depuis (formats : V41 ; « escape » trop générique : V43). Restaurés
-- inactifs, pour retrouver exactement l'état d'une base saine.
INSERT INTO category_keyword (category_id, keyword, active)
SELECT c.id, v.keyword, false
FROM category c
JOIN (
    VALUES
        ('CULTURE', 'conférence'),
        ('CULTURE', 'conférences'),
        ('LEISURE', 'escape')
) AS v(category_code, keyword)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;

-- Graphies observées dans la quarantaine du 08/10/2026, variantes de mots-clés déjà présents (aucun
-- nouveau domaine, aucun mot de format) : « VISITE ARCHI » et « Architectures à Marseille » (45 éléments),
-- tag « VOLLEYBALL » (17 éléments, « volley » et « volley-ball » existaient déjà).
INSERT INTO category_keyword (category_id, keyword)
SELECT c.id, v.keyword
FROM category c
JOIN (
    VALUES
        ('CULTURE', 'archi'),
        ('CULTURE', 'architectures'),
        ('SPORT',   'volleyball')
) AS v(category_code, keyword)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;
