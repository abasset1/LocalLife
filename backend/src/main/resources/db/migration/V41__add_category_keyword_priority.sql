-- Catégorie principale + catégories secondaires (issue #43).
--
-- Plusieurs domaines détectés ne donnent plus automatiquement NEEDS_REVIEW : le meilleur candidat
-- devient la catégorie principale, les autres deviennent secondaires (activity_secondary_category,
-- V36). Le choix du principal est déterministe et explicable : il repose sur la priorité du mot-clé
-- reconnu, stockée ici, jamais dans le code Java.
--
-- Échelle (plus grand = plus fort) :
--   * 100 (défaut) : le mot-clé désigne l'événement lui-même (concert, exposition, marché,
--     sortie nature...). Tous les mots-clés existants y restent, sauf ceux listés plus bas ;
--   * 50 : mot-clé thématique / complémentaire, qui qualifie l'événement sans le définir
--     (dégustation, biodiversité, randonnée, vélo...).
-- Score d'une catégorie = priorité maximale de ses mots-clés reconnus. Égalité entre catégories au
-- plus haut score → ambiguïté réelle → NEEDS_REVIEW (AMBIGUOUS_PRIMARY), jamais de choix arbitraire.
--
-- Idempotente : colonne/contrainte ajoutées si absentes, mises à jour et insertions rejouables.

ALTER TABLE category_keyword ADD COLUMN IF NOT EXISTS priority SMALLINT NOT NULL DEFAULT 100;

ALTER TABLE category_keyword DROP CONSTRAINT IF EXISTS ck_category_keyword_priority;
ALTER TABLE category_keyword
    ADD CONSTRAINT ck_category_keyword_priority CHECK (priority BETWEEN 1 AND 1000);

-- Mots-clés existants thématiques : priorité abaissée explicitement (aucun autre mot-clé existant
-- ne change de priorité).
UPDATE category_keyword ck
SET priority = 50, updated_at = now()
FROM category c
WHERE ck.category_id = c.id
  AND c.code = 'GASTRONOMY'
  AND ck.keyword IN ('dégustation', 'couscous', 'muscat')
  AND ck.priority <> 50;

-- Mots décrivant un format et non un domaine : ils ne doivent pas servir de catégorie (même règle
-- que ACTIVITY_TYPE_VALUES de LegacyCategoryMapper). Désactivés, pas supprimés.
UPDATE category_keyword ck
SET active = false, updated_at = now()
FROM category c
WHERE ck.category_id = c.id
  AND c.code = 'CULTURE'
  AND ck.keyword IN ('conférence', 'conférences')
  AND ck.active;

-- Domaines thématiques nécessaires aux cas multi-catégories du ticket. Aucun mot de format
-- (balade, promenade, atelier, visite...) n'est ajouté.
INSERT INTO category_keyword (category_id, keyword, priority)
SELECT c.id, v.keyword, v.priority
FROM category c
JOIN (
    VALUES
        ('NATURE', 'biodiversité', 50),
        ('SPORT',  'vélo',         50),
        ('SPORT',  'randonnée',    50),
        ('SPORT',  'randonnées',   50)
) AS v(category_code, keyword, priority)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;

-- Traçabilité : catégories secondaires retenues par la décision (codes séparés par des virgules,
-- NULL si aucune).
ALTER TABLE classification_record ADD COLUMN IF NOT EXISTS secondary_category_codes VARCHAR(255);
