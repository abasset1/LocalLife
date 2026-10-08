-- Issue #49, option A : le domaine emploi / formation / insertion devient une catégorie à part entière.
--
-- Avant : aucune catégorie ne correspondait à ce domaine et le moteur forçait NEEDS_REVIEW
-- (EMPLOYMENT_DOMAIN) — environ 2 370 activités de l'extraction du 29/09/2026, soit 42 % de celles qui
-- ont une catégorie d'origine (docs/AUDIT_REFERENTIEL_CATEGORIES.md §4). Désormais les signaux emploi des
-- sources (LegacyCategoryMapper) et les mots-clés ci-dessous (fallback titre) donnent la catégorie
-- EMPLOYMENT, cumulable avec les autres (modèle 1..N, issue #45).
--
-- Les éléments déjà consignés en NEEDS_REVIEW n'ont pas d'activité : ils sont re-décidés au prochain
-- import, sans reprise de données nécessaire.
--
-- Rejouable : ON CONFLICT DO NOTHING partout.

INSERT INTO category (code) VALUES ('EMPLOYMENT')
ON CONFLICT (code) DO NOTHING;

INSERT INTO category_translation (category_id, locale, label, description)
SELECT id, 'fr-FR', 'Emploi & formation',
       'Recrutement, job datings, forums de l''emploi, découverte des métiers, formation professionnelle, '
           || 'alternance, insertion et création d''entreprise.'
FROM category
WHERE code = 'EMPLOYMENT'
ON CONFLICT (category_id, locale) DO NOTHING;

-- Mots-clés du fallback titre. Volontairement exclus car ambigus seuls : « métier(s) » (« Journées
-- européennes des métiers d'art » relève de CULTURE), « formation » (« formation aux premiers secours »),
-- « apprentissage » (« apprentissage de la natation »), « stage » (format).
INSERT INTO category_keyword (category_id, keyword)
SELECT c.id, v.keyword
FROM category c
JOIN (
    VALUES
        ('EMPLOYMENT', 'emploi'),
        ('EMPLOYMENT', 'emplois'),
        ('EMPLOYMENT', 'recrutement'),
        ('EMPLOYMENT', 'recrutements'),
        ('EMPLOYMENT', 'recrute'),
        ('EMPLOYMENT', 'job dating'),
        ('EMPLOYMENT', 'jobdating'),
        ('EMPLOYMENT', 'job'),
        ('EMPLOYMENT', 'jobs'),
        ('EMPLOYMENT', 'embauche'),
        ('EMPLOYMENT', 'entretien d''embauche'),
        ('EMPLOYMENT', 'cv'),
        ('EMPLOYMENT', 'candidature'),
        ('EMPLOYMENT', 'alternance'),
        ('EMPLOYMENT', 'alternants'),
        ('EMPLOYMENT', 'contrat d''apprentissage'),
        ('EMPLOYMENT', 'apprentis'),
        ('EMPLOYMENT', 'formation professionnelle'),
        ('EMPLOYMENT', 'centre de formation'),
        ('EMPLOYMENT', 'organisme de formation'),
        ('EMPLOYMENT', 'reconversion professionnelle'),
        ('EMPLOYMENT', 'insertion professionnelle'),
        ('EMPLOYMENT', 'découverte des métiers'),
        ('EMPLOYMENT', 'découverte métier'),
        ('EMPLOYMENT', 'forum des métiers'),
        ('EMPLOYMENT', 'marché du travail'),
        ('EMPLOYMENT', 'création d''entreprise'),
        ('EMPLOYMENT', 'créer son entreprise'),
        ('EMPLOYMENT', 'entrepreneuriat'),
        ('EMPLOYMENT', 'france travail'),
        ('EMPLOYMENT', 'mission locale'),
        ('EMPLOYMENT', 'intérim'),
        ('EMPLOYMENT', 'interim'),
        ('EMPLOYMENT', 'tousmobilisés')
) AS v(category_code, keyword)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;
