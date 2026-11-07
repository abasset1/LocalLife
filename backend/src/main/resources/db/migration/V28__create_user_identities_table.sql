-- LL-EF-011 : permet à un compte LocalLife d'être associé à une ou
-- plusieurs identités externes (Google pour l'instant, d'autres
-- providers plus tard — voir la section "Architecture" du ticket dans
-- SPRINT_EVOL_FIX.md, qui demande explicitement de prévoir plusieurs
-- providers sans faire dépendre `users` du fonctionnement spécifique de
-- Google).
--
-- Table séparée plutôt que des colonnes directement sur `users` (ex.
-- `google_sub`) : une même table pour tous les providers actuels et
-- futurs (LOCAL n'y figure pas — l'authentification classique reste
-- portée par `users.password_hash`, déjà en place, pas de raison de la
-- dupliquer ici) ; ajouter un provider supplémentaire n'implique aucune
-- migration sur `users` ; et une contrainte d'unicité par (provider,
-- provider_user_id) empêche nativement qu'une même identité externe soit
-- liée à deux comptes LocalLife différents.
--
-- provider_user_id : identifiant stable côté provider (le "sub" du token
-- OpenID Connect pour Google — jamais l'email, qui peut changer côté
-- Google alors que le sub ne change jamais pour un compte donné).
--
-- ON DELETE CASCADE (contrairement à Activity#createdBy, ON DELETE SET
-- NULL, voir V26) : une identité externe n'a aucun sens sans le compte
-- LocalLife auquel elle est rattachée, alors qu'une activité créée
-- reste consultable après suppression de son créateur (voir V26 pour le
-- raisonnement détaillé) — la conservation de l'historique ne s'applique
-- pas ici.
CREATE TABLE user_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_identities_user_id ON user_identities(user_id);
