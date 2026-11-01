-- Alertes personnalisées par mots-clés (offres Premium et Pro, vérifié par l'application : PlanLimits).
--
-- Une alerte peut porter jusqu'à 10 mots-clés ou expressions (« jazz », « marché de Noël »). Une nouvelle activité
-- lui correspond si l'un d'eux apparaît dans son titre ou sa description, au début d'un mot, sans tenir compte des
-- majuscules, des accents ni de la ponctuation (ll_search_normalize, V58, appliqué des deux côtés) : « concert »
-- trouve « Concerts », « noel » trouve « Noël », « art » ne trouve pas « départ ». Avec des catégories, les deux se
-- combinent (Musique ET « jazz ») ; sans catégorie, toutes les catégories sont couvertes. Les mots-clés s'appliquent
-- aussi aux food trucks de l'alerte (nom, cuisine, description).
--
-- keyword : tel que saisi (affichage) ; position : ordre de saisie.
CREATE TABLE activity_alert_keyword (
    alert_id BIGINT       NOT NULL REFERENCES activity_alert (id) ON DELETE CASCADE,
    keyword  VARCHAR(60)  NOT NULL,
    position SMALLINT     NOT NULL,
    PRIMARY KEY (alert_id, keyword)
);
