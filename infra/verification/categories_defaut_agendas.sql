-- Catégories par défaut des agendas thématiques — ÉCRITURE (à blanc par défaut).
-- Contexte : quarantaine_mesure.sql (24/10/2026). Aucune source n'avait de catégorie par défaut ; ces agendas ne
-- publient qu'un type d'événement, et leurs événements sans mot-clé reconnu restent en quarantaine (≈ 7 840).
-- Une catégorie par défaut ne s'applique qu'aux événements qu'aucun mot-clé ne classe ; l'import suivant re-décide
-- toute la quarantaine de la source (ImportService, referentialVersion). Même effet que « Modifier » dans
-- Administration → Agendas → Catégories par défaut.
--
-- Usage (VPS, depuis infra/) :
--   à blanc (rien n'est enregistré, affiche ce qui serait fait) :
--     docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--       < verification/categories_defaut_agendas.sql
--   pour de bon :
--     docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v appliquer=1' \
--       < verification/categories_defaut_agendas.sql
--
-- Garde-fous : chaque agenda est désigné par son id ET son nom ; si un id manque ou porte un autre nom, ou si une
-- catégorie est inconnue, rien n'est écrit. Une catégorie déjà réglée n'est pas dupliquée ; une catégorie par défaut
-- existante n'est jamais retirée. Chaque ajout est tracé dans admin_action_log (journal en ajout seul).
-- Retour arrière : Administration → Agendas → Modifier → décocher, ou
--   DELETE FROM source_default_category WHERE source_id = <id> AND category_id = (SELECT id FROM category WHERE code = '<code>');

\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE reglage (source_id BIGINT PRIMARY KEY, nom TEXT NOT NULL, categorie TEXT NOT NULL) ON COMMIT DROP;
INSERT INTO reglage (source_id, nom, categorie) VALUES
    (129,  'Shotgun',                                'CULTURE'),
    (609,  'AgendaTrad',                             'CULTURE'),
    (322,  'SCARE - Syndicat des Cinémas d''Art%',   'CULTURE'),
    (11,   'Bicentenaire de la Photographie 2026',   'CULTURE'),
    (1407, 'Catalogue En Scène ! 2026-2027',         'CULTURE'),
    (25,   'FESTIVAL CULTURE BAR-BARS',              'CULTURE'),
    (1375, 'Rouen Bibliothèques',                    'CULTURE'),
    (312,  'Mes événements France Travail',          'EMPLOYMENT');

\echo '== 1. Vérification des agendas et des catégories (attendu : ok partout) =='
SELECT r.source_id, coalesce(s.name, '(absent)') AS agenda_en_base, r.categorie,
       CASE WHEN s.id IS NULL THEN 'ERREUR : id absent'
            WHEN s.name NOT ILIKE r.nom THEN 'ERREUR : autre nom'
            WHEN c.id IS NULL THEN 'ERREUR : catégorie inconnue'
            ELSE 'ok' END AS controle,
       (SELECT string_agg(c2.code, ', ' ORDER BY c2.code)
          FROM source_default_category d JOIN category c2 ON c2.id = d.category_id
         WHERE d.source_id = r.source_id) AS deja_regle
FROM reglage r
LEFT JOIN source s ON s.id = r.source_id
LEFT JOIN category c ON c.code = r.categorie
ORDER BY r.source_id;

DO $$
DECLARE
    problemes INT;
BEGIN
    SELECT count(*) INTO problemes
    FROM reglage r
    LEFT JOIN source s ON s.id = r.source_id
    LEFT JOIN category c ON c.code = r.categorie
    WHERE s.id IS NULL OR s.name NOT ILIKE r.nom OR c.id IS NULL;
    IF problemes > 0 THEN
        RAISE EXCEPTION '% agenda(s) ou catégorie(s) ne correspondent pas : rien n''est écrit.', problemes;
    END IF;
END $$;

\echo '== 2. Ajouts (catégories par défaut absentes jusqu''ici) =='
WITH ajouts AS (
    INSERT INTO source_default_category (source_id, category_id)
    SELECT r.source_id, c.id
    FROM reglage r
    JOIN category c ON c.code = r.categorie
    ON CONFLICT (source_id, category_id) DO NOTHING
    RETURNING source_id, category_id
), trace AS (
    INSERT INTO admin_action_log (actor_id, actor_email, action, target_type, target_id, summary, after_value, reason)
    SELECT NULL, 'script infra/verification/categories_defaut_agendas.sql', 'SOURCE_DEFAULT_CATEGORIES', 'SOURCE',
           a.source_id::text, s.name, jsonb_build_object('ajout', c.code),
           'Agenda thématique : événements sans mot-clé reconnu classés par défaut (mesure de la quarantaine du 24/10/2026)'
    FROM ajouts a
    JOIN source s ON s.id = a.source_id
    JOIN category c ON c.id = a.category_id
    RETURNING target_id
)
SELECT a.source_id, s.name AS agenda, c.code AS categorie_ajoutee
FROM ajouts a
JOIN source s ON s.id = a.source_id
JOIN category c ON c.id = a.category_id
ORDER BY a.source_id;

\echo '== 3. Événements en quarantaine concernés (re-décidés au prochain import) =='
SELECT r.source_id, s.name AS agenda, r.categorie, count(cr.id) AS en_quarantaine
FROM reglage r
JOIN source s ON s.id = r.source_id
LEFT JOIN classification_record cr ON cr.source_id = r.source_id AND cr.activity_id IS NULL
GROUP BY r.source_id, s.name, r.categorie
ORDER BY 4 DESC;

\if :{?appliquer}
COMMIT;
\echo '>>> ENREGISTRÉ. Effet au prochain import (environ toutes les 2 h).'
\else
ROLLBACK;
\echo '>>> À BLANC : rien n''a été enregistré. Pour appliquer, relancer avec -v appliquer=1.'
\endif
