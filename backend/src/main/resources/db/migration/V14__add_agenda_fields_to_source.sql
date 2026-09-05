-- LL-EF-005 : gestion dynamique des agendas OpenAgenda depuis l'interface
-- d'administration, en remplacement de la configuration par propriétés
-- (OpenAgendaSourcesConfig, voir application.properties avant ce ticket).
-- L'uid OpenAgenda et le filtre région, jusqu'ici lus une fois au
-- démarrage depuis des variables d'environnement, deviennent des colonnes
-- de la table source, gérables via l'API (SourceController) sans
-- redémarrage. Nullables : seules les sources de type API destinées à
-- être collectées via OpenAgenda utilisent ces deux champs (RSS/MANUAL
-- n'en ont pas besoin).
ALTER TABLE source
    ADD COLUMN agenda_uid VARCHAR(255),
    ADD COLUMN region_filter VARCHAR(255);

-- Reprend les deux agendas jusqu'ici actifs dans application.properties
-- (openagenda.agenda-uid / openagenda.avignon-culture-uid) pour éviter
-- une régression silencieuse au déploiement de ce ticket (plus aucun
-- agenda ne serait collecté sans cette ligne, tant qu'un administrateur
-- ne les aurait pas recréés manuellement). Ces uids ne sont pas des
-- secrets (contrairement à la clé API OpenAgenda, qui reste hors base,
-- voir OpenAgendaCollectorFactory) : les faire migrer en base est la
-- suite logique du passage à une configuration dynamique, pas une
-- exception au principe "pas de secret en base".
-- ⚠️ Le filtre région global (OPENAGENDA_REGION_FILTER, s'il était
-- positionné dans l'environnement de déploiement) n'est PAS repris ici
-- (valeur non versionnée, donc inconnue de cette migration) : à
-- reporter manuellement par Alex sur l'agenda concerné via l'interface
-- d'administration si nécessaire.
INSERT INTO source (name, type, url, status, last_sync_at, agenda_uid, region_filter)
VALUES
    ('OpenAgenda Ministere culture', 'API', NULL, 'ACTIVE', NULL, '86244142', NULL),
    ('OpenAgenda Ville d''Avignon', 'API', NULL, 'ACTIVE', NULL, '79839448', NULL);
