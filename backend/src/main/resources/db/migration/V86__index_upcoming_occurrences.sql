-- Recherche sur la carte (ActivityRepository#findWithinRadius/#findWithinBounds) et liste filtrée par date
-- (#findAllFiltered) : les activités terminées ne sont pas archivées (statut ARCHIVED réservé aux activités
-- disparues de leur source, voir ImportService), elles restent en base et leurs occurrences passées s'accumulent.
--
-- Le filtre de date de ces requêtes compare des jours civils dans le fuseau du lieu
-- ((o.start_at AT TIME ZONE COALESCE(l.timezone, ...))::date) : expression calculée ligne à ligne, qu'aucun index
-- ne peut servir. Chaque occurrence d'une activité, passée comprise, était donc lue et jointe à son lieu.
--
-- Les requêtes ajoutent désormais une borne directe sur la fin de l'occurrence
-- (COALESCE(o.end_at, o.start_at) >= jour recherché - 2 jours, minuit UTC) : toujours vraie pour une occurrence
-- retenue (un jour civil local commence au plus tôt 14 h avant minuit UTC), elle ne change aucun résultat et permet
-- à cet index d'écarter les occurrences terminées sans les lire.
--
-- Index complet (sans condition sur status) : il commence par schedule_id et remplace donc idx_occurrence_schedule_id
-- (V19) pour toute recherche des occurrences d'un programme (clé étrangère, suppressions, import). Garder les deux
-- laisserait le planificateur choisir l'ancien, la borne calculée avec now() étant mal estimée.
CREATE INDEX idx_occurrence_schedule_last_at ON occurrence (schedule_id, (COALESCE(end_at, start_at)));
DROP INDEX idx_occurrence_schedule_id;
