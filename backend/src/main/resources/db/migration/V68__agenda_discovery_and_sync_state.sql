-- Sprint « Découverte exhaustive et import fiable des agendas » (AGD-02/03/05).
--
-- 1. Catalogue des agendas découverts, distinct des sources : découvrir l'annuaire ne crée plus de source
--    (donc ne déclenche plus aucune collecte). Une source n'est créée que pour les agendas qu'un
--    administrateur sélectionne. Un agenda est identifié par (provider, agenda_uid) : une redécouverte
--    met à jour la ligne au lieu de la dupliquer.
CREATE TABLE discovered_agenda (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    agenda_uid VARCHAR(255) NOT NULL,
    title VARCHAR(512),
    slug VARCHAR(255),
    official BOOLEAN,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    last_run_id BIGINT,
    CONSTRAINT uq_discovered_agenda_provider_uid UNIQUE (provider, agenda_uid)
);

CREATE INDEX idx_discovered_agenda_title ON discovered_agenda (lower(title));

-- 2. Parcours de l'annuaire, par lots de pages, reprenable : le curseur de la dernière page traitée est
--    enregistré dans la même transaction que les agendas de cette page. Le curseur est celui renvoyé par
--    le fournisseur (valeurs de tri), sans donnée sensible ; la clé API n'est jamais stockée.
--    IN_PROGRESS : reprenable depuis le curseur ; COMPLETE : marqueur de fin du fournisseur atteint ;
--    FAILED : anomalie de pagination (curseur répété, réponse incohérente), non reprenable ;
--    ABANDONED : remplacé par un nouveau parcours à la demande d'un administrateur.
CREATE TABLE agenda_discovery_run (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    cursor TEXT,
    pages_read INTEGER NOT NULL DEFAULT 0,
    examined INTEGER NOT NULL DEFAULT 0,
    new_count INTEGER NOT NULL DEFAULT 0,
    already_known INTEGER NOT NULL DEFAULT 0,
    ignored INTEGER NOT NULL DEFAULT 0,
    errors INTEGER NOT NULL DEFAULT 0,
    provider_total INTEGER,
    stop_reason VARCHAR(1000),
    started_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    CONSTRAINT chk_agenda_discovery_run_scope CHECK (scope IN ('OFFICIAL', 'ALL')),
    CONSTRAINT chk_agenda_discovery_run_status CHECK (status IN ('IN_PROGRESS', 'COMPLETE', 'FAILED', 'ABANDONED'))
);

-- Un seul parcours en cours par fournisseur et périmètre : deux reprises concurrentes ne peuvent pas
-- avancer deux curseurs différents.
CREATE UNIQUE INDEX uq_agenda_discovery_run_in_progress
    ON agenda_discovery_run (provider, scope) WHERE status = 'IN_PROGRESS';

-- 3. État de synchronisation par source : dernier essai (quel qu'en soit le résultat) séparé du dernier
--    succès complet. source.last_sync_at reste « dernier import complet réussi » et n'est plus avancé par
--    une collecte partielle ; cette table porte le dernier essai et son statut.
CREATE TABLE source_sync_state (
    source_id BIGINT PRIMARY KEY REFERENCES source (id) ON DELETE CASCADE,
    last_attempt_at TIMESTAMP NOT NULL,
    last_status VARCHAR(20) NOT NULL,
    last_message VARCHAR(1000),
    last_pages_read INTEGER NOT NULL DEFAULT 0,
    last_received INTEGER NOT NULL DEFAULT 0,
    last_duration_ms BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_source_sync_state_status CHECK (last_status IN ('SUCCESS', 'PARTIAL', 'FAILED'))
);
