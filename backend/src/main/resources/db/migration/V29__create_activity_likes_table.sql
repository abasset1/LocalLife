-- LS-002 : relation entre un utilisateur et une activité qu'il a
-- "likée" ("Créer une relation entre l'utilisateur et l'événement [...]
-- via une entité/table de favoris ou de likes. Cette relation doit être
-- unique afin d'empêcher les doublons" — critère explicite du ticket).
--
-- Contrainte UNIQUE (user_id, activity_id) : empêche nativement qu'un
-- même utilisateur like deux fois la même activité (critère explicite
-- "un utilisateur ne peut liker qu'une seule fois le même événement"),
-- sans dépendre uniquement d'une vérification applicative qui pourrait
-- être contournée par une course entre deux requêtes concurrentes.
--
-- ON DELETE CASCADE sur les deux clés étrangères (contrairement à
-- Activity#createdBy, ON DELETE SET NULL, voir V26) : un like n'a de
-- sens que rattaché à un utilisateur ET une activité précis, aucune
-- valeur à conserver l'un sans l'autre — répond explicitement au critère
-- "aucune donnée orpheline ne doit rester en base" si l'un des deux
-- venait à être supprimé. Ne couvre pas le cas d'une activité annulée
-- (LL-EF-010, changement de statut, pas une suppression) : dans ce cas le
-- like est volontairement conservé (l'utilisateur pourrait la re-liker
-- sans perdre l'historique) mais filtré à la lecture — voir
-- LikeService#getLikedActivities.
CREATE TABLE activity_likes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_id BIGINT NOT NULL REFERENCES activity(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (user_id, activity_id)
);

CREATE INDEX idx_activity_likes_user_id ON activity_likes(user_id);
CREATE INDEX idx_activity_likes_activity_id ON activity_likes(activity_id);
