-- Issue #50 : un seul référentiel, en base, pour les catégories d'origine / tags de la source ET pour les
-- titres. Jusqu'ici les signaux source passaient par LegacyCategoryMapper, une liste fermée codée en Java
-- (correspondances exactes, jetons emploi...). Ses règles sont reprises ici dans category_keyword ; la
-- classe Java est supprimée. Audit et chiffres : docs/CATEGORIES_CLASSIFICATION.md §13.
--
-- scope : où un mot-clé s'applique.
--   * ALL    (défaut) : titres ET catégories d'origine / tags de la source ;
--   * SOURCE : catégories d'origine / tags seulement. Pour les termes fiables dans les mots-clés
--              structurés d'un agenda (« Métiers du BTP », « Formation », « Insertion ») mais trop
--              ambigus dans un titre libre (« Formation aux premiers secours », « Travail du bois »).
--
-- Rejouable : colonne/contrainte ajoutées si absentes, insertions ON CONFLICT DO NOTHING.

ALTER TABLE category_keyword ADD COLUMN IF NOT EXISTS scope VARCHAR(10) NOT NULL DEFAULT 'ALL';
ALTER TABLE category_keyword DROP CONSTRAINT IF EXISTS ck_category_keyword_scope;
ALTER TABLE category_keyword ADD CONSTRAINT ck_category_keyword_scope CHECK (scope IN ('ALL', 'SOURCE'));

INSERT INTO category_keyword (category_id, keyword, scope)
SELECT c.id, v.keyword, v.scope
FROM category c
JOIN (
    VALUES
        -- Jetons et libellés emploi de LegacyCategoryMapper absents du référentiel (sources seulement).
        ('EMPLOYMENT', 'métier',                       'SOURCE'),
        ('EMPLOYMENT', 'métiers',                      'SOURCE'),
        ('EMPLOYMENT', 'formation',                    'SOURCE'),
        ('EMPLOYMENT', 'formations',                   'SOURCE'),
        ('EMPLOYMENT', 'insertion',                    'SOURCE'),
        ('EMPLOYMENT', 'apprentissage',                'SOURCE'),
        ('EMPLOYMENT', 'travail',                      'SOURCE'),
        ('EMPLOYMENT', 'orientation',                  'SOURCE'),
        ('EMPLOYMENT', 'entreprise',                   'SOURCE'),
        ('EMPLOYMENT', 'entreprises',                  'SOURCE'),
        ('EMPLOYMENT', 'tousenimmersion',              'SOURCE'),
        ('EMPLOYMENT', 'qpv',                          'SOURCE'),
        ('EMPLOYMENT', 's''informer',                  'SOURCE'),
        ('EMPLOYMENT', 'se préparer',                  'SOURCE'),
        ('EMPLOYMENT', 'les clés pour réussir',        'SOURCE'),
        ('EMPLOYMENT', 'détection de potentiel',       'SOURCE'),
        ('EMPLOYMENT', 'mobilisation 50 ans et plus',  'SOURCE'),
        ('EMPLOYMENT', '1 jeune 1 solution',           'SOURCE'),
        -- Correspondances exactes de LegacyCategoryMapper non couvertes par un mot-clé existant.
        ('CULTURE',    'culture',                      'SOURCE'),
        ('CULTURE',    'projection',                   'SOURCE'),
        -- Artisanat d'art, pas le domaine emploi : un libellé entier prime sur « métiers » (règle de
        -- la valeur entière, voir KeywordCategoryMatcher).
        ('CULTURE',    'métiers d''art',               'ALL'),
        ('CULTURE',    'journées européennes des métiers d''art', 'ALL'),
        -- Graphie réellement observée dans l'extraction (comme « théatre », V43).
        ('CULTURE',    'theâtre',                      'ALL')
) AS v(category_code, keyword, scope)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;
