-- Enrich category keyword referential from the latest UNCLASSIFIED title audit.
-- Idempotent: existing keywords are left unchanged.

INSERT INTO category_keyword (category_id, keyword)
SELECT c.id, v.keyword
FROM category c
JOIN (
    VALUES
        -- CULTURE
        ('CULTURE', 'architecture'),
        ('CULTURE', 'architecte'),
        ('CULTURE', 'architectes'),
        ('CULTURE', 'architecturale'),
        ('CULTURE', 'architecturales'),
        ('CULTURE', 'architectural'),
        ('CULTURE', 'architecturaux'),
        ('CULTURE', 'architecture contemporaine'),
        ('CULTURE', 'conférence'),
        ('CULTURE', 'conférences'),
        ('CULTURE', 'musée'),
        ('CULTURE', 'musées'),
        ('CULTURE', 'photographie'),
        ('CULTURE', 'photographies'),
        ('CULTURE', 'photo'),
        ('CULTURE', 'dessin'),
        ('CULTURE', 'dessins'),
        ('CULTURE', 'peinture'),
        ('CULTURE', 'peintre'),
        ('CULTURE', 'lecture'),
        ('CULTURE', 'lectures'),
        ('CULTURE', 'livre'),
        ('CULTURE', 'livres'),
        ('CULTURE', 'roman'),
        ('CULTURE', 'romans'),
        ('CULTURE', 'poésie'),
        ('CULTURE', 'littérature'),
        ('CULTURE', 'danse'),
        ('CULTURE', 'chanson'),
        ('CULTURE', 'chant'),
        ('CULTURE', 'cirque'),
        ('CULTURE', 'cabaret'),
        ('CULTURE', 'vernissage'),

        -- LEISURE
        ('LEISURE', 'escape game'),
        ('LEISURE', 'escape'),
        ('LEISURE', 'quizz'),
        ('LEISURE', 'jeu'),
        ('LEISURE', 'jeux'),
        ('LEISURE', 'jeu de piste'),
        ('LEISURE', 'jeu de plateau'),
        ('LEISURE', 'kermesse'),
        ('LEISURE', 'fête foraine'),

        -- GASTRONOMY
        ('GASTRONOMY', 'couscous'),
        ('GASTRONOMY', 'muscat')
) AS v(category_code, keyword)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;
