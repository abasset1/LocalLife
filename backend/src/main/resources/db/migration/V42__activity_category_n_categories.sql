-- Issue #45 : une activité a 1..N catégories, sans notion de catégorie principale/secondaire.
--
-- Modèle cible : activity_category(activity_id, category_id), une ligne par catégorie.
-- Remplace activity.primary_category_id (V37) et activity_secondary_category (V36), ainsi que le
-- mécanisme de priorité des mots-clés (V41) devenu sans objet : toutes les catégories détectées
-- sont conservées, aucune n'est choisie au détriment d'une autre.
--
-- Rejouable : chaque étape vérifie l'état courant (IF [NOT] EXISTS, ON CONFLICT, information_schema).

CREATE TABLE IF NOT EXISTS activity_category (
    id          BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activity(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES category(id),
    CONSTRAINT uq_activity_category UNIQUE (activity_id, category_id)
);
CREATE INDEX IF NOT EXISTS idx_activity_category_activity_id ON activity_category (activity_id);
CREATE INDEX IF NOT EXISTS idx_activity_category_category_id ON activity_category (category_id);

-- Reprise des données : catégorie principale puis catégories secondaires, toutes conservées.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'activity' AND column_name = 'primary_category_id') THEN
        INSERT INTO activity_category (activity_id, category_id)
        SELECT id, primary_category_id FROM activity WHERE primary_category_id IS NOT NULL
        ON CONFLICT (activity_id, category_id) DO NOTHING;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'activity_secondary_category') THEN
        INSERT INTO activity_category (activity_id, category_id)
        SELECT activity_id, category_id FROM activity_secondary_category
        ON CONFLICT (activity_id, category_id) DO NOTHING;
    END IF;
END $$;

-- Traçabilité : classification_record.secondary_category_codes (V41) devient category_codes,
-- l'ensemble des catégories retenues pour l'activité (codes séparés par des virgules).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'classification_record' AND column_name = 'secondary_category_codes') THEN
        ALTER TABLE classification_record RENAME COLUMN secondary_category_codes TO category_codes;
    END IF;
END $$;
ALTER TABLE classification_record ADD COLUMN IF NOT EXISTS category_codes VARCHAR(255);

UPDATE classification_record cr
SET category_codes = codes.list
FROM (
    SELECT ac.activity_id, string_agg(c.code, ',' ORDER BY ac.id) AS list
    FROM activity_category ac
    JOIN category c ON c.id = ac.category_id
    GROUP BY ac.activity_id
) AS codes
WHERE cr.activity_id = codes.activity_id;

-- Suppression des anciennes structures.
ALTER TABLE activity DROP COLUMN IF EXISTS primary_category_id;
DROP TABLE IF EXISTS activity_secondary_category;
ALTER TABLE category_keyword DROP CONSTRAINT IF EXISTS ck_category_keyword_priority;
ALTER TABLE category_keyword DROP COLUMN IF EXISTS priority;

-- Enrichissement du référentiel (exemple du ticket « Balade nature à vélo » → NATURE + SPORT).
INSERT INTO category_keyword (category_id, keyword)
SELECT c.id, v.keyword
FROM category c
JOIN (VALUES ('NATURE', 'nature')) AS v(category_code, keyword) ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;

-- « Au moins une catégorie » reste une contrainte réelle en base (comme le NOT NULL de V37) :
-- vérifiée en fin de transaction (DEFERRABLE INITIALLY DEFERRED), ce qui permet d'insérer
-- l'activité puis ses catégories dans la même transaction. Une activité créée sans catégorie, ou
-- dont la dernière catégorie est retirée, fait échouer le commit.
CREATE OR REPLACE FUNCTION check_activity_has_category() RETURNS trigger AS $$
DECLARE
    checked_activity_id BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'activity' THEN
        checked_activity_id := NEW.id;
    ELSE
        checked_activity_id := OLD.activity_id;
    END IF;
    IF EXISTS (SELECT 1 FROM activity WHERE id = checked_activity_id)
            AND NOT EXISTS (SELECT 1 FROM activity_category WHERE activity_id = checked_activity_id) THEN
        RAISE EXCEPTION 'activity % must have at least one category', checked_activity_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_activity_has_category ON activity;
CREATE CONSTRAINT TRIGGER trg_activity_has_category
    AFTER INSERT ON activity
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_activity_has_category();

DROP TRIGGER IF EXISTS trg_activity_category_not_last ON activity_category;
CREATE CONSTRAINT TRIGGER trg_activity_category_not_last
    AFTER DELETE OR UPDATE OF activity_id ON activity_category
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_activity_has_category();
