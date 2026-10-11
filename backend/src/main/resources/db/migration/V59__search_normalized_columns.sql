-- #91 — Recherche globale : textes normalisés calculés à l'écriture.
--
-- Mesure (EXPLAIN ANALYZE, 20 000 activités, voir docs/02_Architecture/GLOBAL_SEARCH_CONTRACT.md) :
-- normaliser les descriptions à chaque recherche coûtait à lui seul ~6 s par requête. Les textes
-- normalisés sont donc calculés une fois, à l'insertion ou la modification de la ligne, par des colonnes
-- générées (ll_search_normalize, V58, est IMMUTABLE). La recherche ne fait plus que des comparaisons de
-- chaînes déjà normalisées.
--
-- `||` plutôt que concat_ws : concat_ws n'est pas IMMUTABLE et ne peut pas servir dans une colonne générée.
--
-- Colonnes en lecture seule pour l'application : PostgreSQL les recalcule, elles ne figurent pas dans
-- les entités Java (Spring Data JDBC n'écrit que les propriétés mappées). Aucun index ici : non
-- justifié par la mesure à ce volume (voir le contrat, section Performance).

ALTER TABLE activity
    ADD COLUMN search_title TEXT GENERATED ALWAYS AS (COALESCE(ll_search_normalize(title), '')) STORED,
    ADD COLUMN search_description TEXT
        GENERATED ALWAYS AS (COALESCE(ll_search_normalize(description), '')) STORED,
    ADD COLUMN search_category TEXT GENERATED ALWAYS AS (COALESCE(ll_search_normalize(category), '')) STORED,
    ADD COLUMN search_place TEXT
        GENERATED ALWAYS AS (COALESCE(ll_search_normalize(COALESCE(city, '') || ' ' || COALESCE(address, '')), '')) STORED;

ALTER TABLE location
    ADD COLUMN search_place TEXT
        GENERATED ALWAYS AS (COALESCE(ll_search_normalize(COALESCE(city, '') || ' ' || COALESCE(address, '')), '')) STORED;
