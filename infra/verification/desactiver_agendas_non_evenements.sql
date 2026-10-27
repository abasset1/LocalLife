-- Désactivation des agendas qui ne publient pas d'événements — ÉCRITURE (à blanc par défaut).
-- Contexte : agendas_candidats.sql (25/10/2026), section 3. Les « Catalogues … des structures d'accueil et
-- d'hébergement » (national et départementaux) décrivent des centres de vacances et des auberges, pas des événements :
-- ils remplissent la quarantaine de classification et publient quelques fiches sans date d'événement réelle.
-- « Agenda de test » (1341) est un agenda d'essai resté actif.
--
-- Effets :
--   1. l'agenda passe INACTIVE : l'import ne le collecte plus (ImportService.isCollectible) ;
--   2. ses activités PUBLISHED passent ARCHIVED : un agenda inactif n'est plus collecté, donc plus rien n'archiverait
--      celles qui restent visibles. Une activité dans un autre statut (annulée, en attente…) n'est pas modifiée.
-- Réversible : réactiver l'agenda (Administration → Agendas → Modifier → ACTIVE) ; au prochain import, ses activités
-- archivées encore présentes dans la source sont republiées (l'import ne saute jamais une activité archivée).
--
-- Usage (VPS, depuis infra/) :
--   à blanc (rien n'est enregistré, affiche ce qui serait fait) :
--     docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--       < verification/desactiver_agendas_non_evenements.sql
--   pour de bon :
--     docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v appliquer=1' \
--       < verification/desactiver_agendas_non_evenements.sql
--
-- Garde-fous : les catalogues sont désignés par un motif de nom strict (début et fin du libellé OpenAgenda), l'agenda
-- de test par son id ET son nom ; s'il manque, rien n'est écrit. Chaque désactivation est tracée dans admin_action_log.

\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE cible (source_id BIGINT PRIMARY KEY, nom TEXT NOT NULL, statut_avant TEXT NOT NULL,
                         motif TEXT NOT NULL) ON COMMIT DROP;

INSERT INTO cible (source_id, nom, statut_avant, motif)
SELECT s.id, s.name, s.status, 'Annuaire de structures d''accueil et d''hébergement, pas des événements'
FROM source s
WHERE s.name ~* '^Catalogue (national|départemental) des structures d.accueil et d.hébergement( - .+)?$';

INSERT INTO cible (source_id, nom, statut_avant, motif)
SELECT s.id, s.name, s.status, 'Agenda d''essai resté actif'
FROM source s
WHERE s.id = 1341 AND s.name = 'Agenda de test';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cible WHERE source_id = 1341) THEN
        RAISE EXCEPTION 'Agenda 1341 « Agenda de test » introuvable sous ce nom : rien n''est écrit.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM cible WHERE source_id <> 1341) THEN
        RAISE EXCEPTION 'Aucun catalogue de structures d''accueil trouvé : rien n''est écrit.';
    END IF;
END $$;

\echo '== 1. Agendas visés (vérifier qu''il n''y a que des catalogues de structures d''accueil et l''agenda de test) =='
SELECT c.source_id, left(c.nom, 90) AS agenda, c.statut_avant,
       (SELECT count(*) FROM activity a WHERE a.source_id = c.source_id AND a.status = 'PUBLISHED') AS publiees,
       (SELECT count(*) FROM classification_record cr
         WHERE cr.source_id = c.source_id AND cr.activity_id IS NULL) AS en_quarantaine
FROM cible c
ORDER BY 5 DESC, c.source_id;

SELECT count(*) AS agendas_vises,
       count(*) FILTER (WHERE statut_avant = 'ACTIVE') AS encore_actifs
FROM cible;

\echo '== 2. Désactivation des agendas encore actifs =='
WITH desactives AS (
    UPDATE source s SET status = 'INACTIVE'
    FROM cible c
    WHERE s.id = c.source_id AND s.status = 'ACTIVE'
    RETURNING s.id
), archivees AS (
    UPDATE activity a SET status = 'ARCHIVED'
    FROM cible c
    WHERE a.source_id = c.source_id AND a.status = 'PUBLISHED'
    RETURNING a.source_id
), par_agenda AS (
    SELECT c.source_id, c.nom, c.statut_avant, c.motif,
           (c.source_id IN (SELECT id FROM desactives)) AS desactive,
           (SELECT count(*) FROM archivees x WHERE x.source_id = c.source_id) AS archivees
    FROM cible c
), trace AS (
    INSERT INTO admin_action_log (actor_id, actor_email, action, target_type, target_id, summary,
                                  before_value, after_value, reason)
    SELECT NULL, 'script infra/verification/desactiver_agendas_non_evenements.sql', 'SOURCE_DEACTIVATE', 'SOURCE',
           p.source_id::text, p.nom,
           jsonb_build_object('status', p.statut_avant),
           jsonb_build_object('status', 'INACTIVE', 'activites_archivees', p.archivees),
           p.motif
    FROM par_agenda p
    WHERE p.desactive OR p.archivees > 0
    RETURNING target_id
)
SELECT count(*) FILTER (WHERE desactive) AS agendas_desactives,
       coalesce(sum(archivees), 0) AS activites_archivees,
       (SELECT count(*) FROM trace) AS lignes_journal
FROM par_agenda;

\if :{?appliquer}
COMMIT;
\echo '>>> ENREGISTRÉ. Ces agendas ne sont plus collectés ; leurs activités publiées sont archivées.'
\else
ROLLBACK;
\echo '>>> À BLANC : rien n''a été enregistré. Pour appliquer, relancer avec -v appliquer=1.'
\endif
