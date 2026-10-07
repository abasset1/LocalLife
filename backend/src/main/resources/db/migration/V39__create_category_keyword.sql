-- Fallback de classification par le titre (ticket « Classification par mots-clés du titre »,
-- docs/TICKET_TITLE_KEYWORD_CLASSIFICATION.md).
--
-- Lorsque les keywords de la source sont absents ou vides, ClassificationService cherche dans le
-- titre les mots-clés de ce référentiel. Les mots-clés métier vivent ICI, jamais dans le code Java.
--
-- * category_id référence la table `category` existante (9 catégories V1) : aucune nouvelle taxonomie.
-- * `keyword` est stocké tel quel, accents compris (« marché » ≠ « marche ») : la comparaison
--   (KeywordMatcher) ignore la casse, les espaces et les séparateurs, jamais les accents.
-- * `active` permet de désactiver un mot-clé sans le supprimer.
-- * Pas de colonne `priority` : une ambiguïté entre catégories n'est jamais masquée en V1.
CREATE TABLE category_keyword (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    keyword     VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_category_keyword_not_blank CHECK (btrim(keyword) <> ''),
    CONSTRAINT uq_category_keyword UNIQUE (category_id, keyword)
);

CREATE INDEX idx_category_keyword_active ON category_keyword (active);

-- Traçabilité (réutilise classification_record) : origine de la décision et mot(s)-clé(s)
-- reconnu(s). NULL pour les classifications issues des keywords source (comportement inchangé).
ALTER TABLE classification_record
    ADD COLUMN classification_source VARCHAR(30),
    ADD COLUMN matched_keywords      VARCHAR(500);

-- Jeu initial volontairement réduit : mots-clés précis et auditables, une catégorie V1 existante
-- chacun. Les ajouter/retirer se fait par migration ou en base, sans toucher au code.
-- 'vide-grenier' couvre aussi « vide grenier » (séparateurs équivalents).
--
-- Prudence sur les faux positifs : un mot isolé n'est pas toujours fiable. Le mot « marché »
-- seul est ambigu (« Marché de la poésie », « marché de l'art » relèvent plutôt de CULTURE) ;
-- il n'est donc PAS seedé seul, mais sous forme d'expressions qui désignent sans ambiguïté un
-- marché commerçant. Un titre qui ne contient que « marché » reste UNCLASSIFIED:NO_SIGNAL
-- (relecture/extension par décision humaine) plutôt qu'une catégorie forcée.
-- Les autres mots-clés ont été relus un à un : pas d'ambiguïté réelle à ce stade ; les cas
-- mixtes (« Concert au marché de Noël ») partent en NEEDS_REVIEW par construction.
INSERT INTO category_keyword (category_id, keyword)
SELECT category.id, v.keyword
FROM category
JOIN (VALUES
    ('CULTURE',    'théâtre'),
    ('CULTURE',    'exposition'),
    ('CULTURE',    'concert'),
    ('GASTRONOMY', 'dégustation'),
    ('MARKETS',    'marché de Noël'),
    ('MARKETS',    'marché de producteurs'),
    ('MARKETS',    'marché artisanal'),
    ('MARKETS',    'marché paysan'),
    ('MARKETS',    'marché nocturne'),
    ('MARKETS',    'vide-grenier'),
    ('MARKETS',    'brocante'),
    ('COMMERCE',   'braderie'),
    ('SPORT',      'football'),
    ('SPORT',      'rugby'),
    ('NATURE',     'sortie nature'),
    ('LEISURE',    'jeux de société'),
    ('CITIZEN',    'réunion publique')
) AS v(code, keyword) ON v.code = category.code;
