-- Décision produit du 08/10/2026 (Alex) : quatre mots de format deviennent des mots-clés de catégorie.
--   * « visite », « visite guidée » → TOURISM + CULTURE ;
--   * « atelier », « conférence »   → CULTURE.
--
-- Jusqu'ici un format ne décidait jamais la catégorie (V41, V43) : ces mots étaient exclus, et
-- « conférence » / « conférences » désactivés par V41. Ils dominaient la quarantaine de l'import du
-- 08/10/2026 (« visite » : 195 éléments, « atelier » : 136, « conférence » : 33 sur 1 187 non classés).
--
-- Conséquence assumée : un titre ou un tag contenant « atelier » reçoit CULTURE en plus de son domaine
-- (« Atelier CV et entretien d'embauche » → EMPLOYMENT + CULTURE). Les autres formats et les publics
-- (balade, festival, rencontre, jeune public...) restent exclus.
--
-- Rejouable : insertions ON CONFLICT DO NOTHING, réactivation idempotente.

INSERT INTO category_keyword (category_id, keyword)
SELECT c.id, v.keyword
FROM category c
JOIN (
    VALUES
        ('TOURISM', 'visite'),
        ('TOURISM', 'visites'),
        ('TOURISM', 'visite guidée'),
        ('TOURISM', 'visites guidées'),
        ('CULTURE', 'visite'),
        ('CULTURE', 'visites'),
        ('CULTURE', 'visite guidée'),
        ('CULTURE', 'visites guidées'),
        ('CULTURE', 'atelier'),
        ('CULTURE', 'ateliers'),
        ('CULTURE', 'conférence'),
        ('CULTURE', 'conférences')
) AS v(category_code, keyword)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;

-- « conférence » / « conférences » existent déjà, inactifs (V41) : réactivés.
UPDATE category_keyword ck
SET active = true, updated_at = now()
FROM category c
WHERE ck.category_id = c.id
  AND c.code = 'CULTURE'
  AND ck.keyword IN ('conférence', 'conférences')
  AND NOT ck.active;
