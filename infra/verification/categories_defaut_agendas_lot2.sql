-- Catégories par défaut des agendas thématiques, lot 2 — ÉCRITURE (à blanc par défaut).
-- Lot 1 : categories_defaut_agendas.sql (8 agendas, appliqué le 24/10/2026, +5 255 événements publiés).
-- Contexte : agendas_candidats.sql (25/10/2026), 120 agendas les plus chargés en quarantaine actuelle. Retenus :
-- ceux dont le nom désigne un seul domaine (bibliothèques, musées, scènes, culture, randonnée, emploi), le plus
-- souvent confirmé par la catégorie dominante de leurs activités publiées. Écartés : les agendas généralistes
-- (villes, métropoles), même à 90 % culture, et les cas incertains (sciences, cultes, universités). ≈ 4 130
-- événements en quarantaine. Noms désignés par leur début (« … % ») : la liste source les tronquait à 45 caractères.
-- Une catégorie par défaut ne s'applique qu'aux événements qu'aucun mot-clé ne classe ; l'import suivant re-décide
-- toute la quarantaine de la source (ImportService, referentialVersion). Même effet que « Modifier » dans
-- Administration → Agendas → Catégories par défaut.
--
-- Usage (VPS, depuis infra/) :
--   à blanc (rien n'est enregistré, affiche ce qui serait fait) :
--     docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--       < verification/categories_defaut_agendas_lot2.sql
--   pour de bon :
--     docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v appliquer=1' \
--       < verification/categories_defaut_agendas_lot2.sql
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
    (434 , 'Plan Libre, la gazette%',                        'CULTURE'),
    (439 , 'Culture Versailles%',                            'CULTURE'),
    (624 , 'La culture en continu%',                         'CULTURE'),
    (4   , 'OpenAgenda Ministere culture%',                  'CULTURE'),
    (394 , 'Le réseau des bibliothèques et médiathèques d%', 'CULTURE'),
    (2267, 'Agenda culturel tourquennois%',                  'CULTURE'),
    (3879, 'Union Fédérale d%Intervention des Structures%',  'CULTURE'),
    (301 , 'Bibliothèques de Toulouse%',                     'CULTURE'),
    (703 , 'Programmation culturelle du réseau des médiat%', 'CULTURE'),
    (4549, 'Musées de Genève%',                              'CULTURE'),
    (1225, 'Centre des Monuments Nationaux%',                'CULTURE'),
    (278 , 'Scènes Culturelles - Ville de Genève%',          'CULTURE'),
    (1328, 'Agenda culturel Grand-Est%',                     'CULTURE'),
    (161 , 'Agenda Médiathèques Orléans%',                   'CULTURE'),
    (2322, 'La C%ART%',                                      'CULTURE'),
    (484 , 'Agenda Culturel Scène des Mondes%',              'CULTURE'),
    (2883, 'Réunion des Opéras de France%',                  'CULTURE'),
    (705 , 'Bibliothèque municipale de Nantes%',             'CULTURE'),
    (514 , 'Centres culturels%',                             'CULTURE'),
    (2754, 'Bibliothèques de Marseille%',                    'CULTURE'),
    (3020, 'Le RIF%',                                        'CULTURE'),
    (3566, 'Bibliothèques de Rennes%',                       'CULTURE'),
    (573 , 'Musée d%arts de Nantes%',                        'CULTURE'),
    (604 , 'Villeurbanne Médiathèques%',                     'CULTURE'),
    (1250, 'LA MURMURE%',                                    'CULTURE'),
    (2420, 'Conservatoire à Rayonnement Régional de Versa%', 'CULTURE'),
    (114 , 'Bibliothèques de Bordeaux%',                     'CULTURE'),
    (1075, 'Zone Franche%',                                  'CULTURE'),
    (90  , 'Culture Nevers%',                                'CULTURE'),
    (367 , 'Réseau des médiathèques & Archives du Mans%',    'CULTURE'),
    (684 , 'Culture Châtellerault%',                         'CULTURE'),
    (2412, 'Le Cratère - Scène nationale d%Alès%',           'CULTURE'),
    (4232, 'Culture - Boulogne-sur-Mer%',                    'CULTURE'),
    (709 , 'Tableau de bord - Agenda du Département théât%', 'CULTURE'),
    (3241, 'Sites & Cités%',                                 'CULTURE'),
    (512 , 'Histoire de Lire%',                              'CULTURE'),
    (4676, 'Agence C%est à Dire%',                           'CULTURE'),
    (137 , 'Le millepattes%',                                'SPORT'),
    (456 , 'Cap Métiers%',                                   'EMPLOYMENT');

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
    SELECT NULL, 'script infra/verification/categories_defaut_agendas_lot2.sql', 'SOURCE_DEFAULT_CATEGORIES', 'SOURCE',
           a.source_id::text, s.name, jsonb_build_object('ajout', c.code),
           'Agenda thématique : événements sans mot-clé reconnu classés par défaut (lot 2, agendas_candidats.sql du 25/10/2026)'
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
