-- Disable the generic title keyword "marché" for MARKETS.
--
-- "marché" is too broad for automatic classification and can misclassify
-- cultural/editorial events such as "Marché de la poésie".
-- Keep the V39 seed immutable: this correction is applied as a new migration.
UPDATE category_keyword
SET active = false,
    updated_at = now()
WHERE keyword = 'marché'
  AND category_id = (SELECT id FROM category WHERE code = 'MARKETS');
