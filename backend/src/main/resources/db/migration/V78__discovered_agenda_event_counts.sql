-- Nombre d'événements en cours et à venir de chaque agenda découvert, pour ne proposer à l'activation que les
-- agendas vivants.
--
-- Constat (beta, 31/10/2026) : 22 501 agendas découverts, dont 17 758 sans source ; la plupart n'ont aucun événement,
-- ou un seul, passé. L'annuaire OpenAgenda donne ces nombres sans appel supplémentaire quand on lui demande le résumé
-- de chaque agenda (GET /v2/agendas?fields[]=summary → summary.publishedEvents.current / .upcoming) : la découverte
-- les enregistre à chaque passage.
--
-- NULL : pas encore compté (agenda découvert avant cette migration et pas revu depuis) ; une nouvelle découverte les
-- renseigne.
ALTER TABLE discovered_agenda
    ADD COLUMN current_events INTEGER,
    ADD COLUMN upcoming_events INTEGER,
    ADD COLUMN events_counted_at TIMESTAMP;

-- Filtre « avec des événements en cours ou à venir » de l'administration.
CREATE INDEX idx_discovered_agenda_with_events ON discovered_agenda (provider)
    WHERE coalesce(current_events, 0) + coalesce(upcoming_events, 0) > 0;
