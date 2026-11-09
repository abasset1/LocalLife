-- Révocation d'une session précise (correctif sécurité du 06/11/2026). Jusqu'ici, seul un changement de mot de
-- passe (ou le « logout everywhere » de l'administration) invalidait un jeton avant son expiration, et en bloc
-- (toutes les sessions via users.tokens_valid_after) ; la déconnexion de l'utilisateur ne faisait qu'effacer le
-- cookie, laissant le jeton valable jusqu'à 24 h s'il avait été capté ailleurs.
--
-- On enregistre désormais, à la déconnexion, le jti (identifiant unique du jeton) de LA session fermée : le filtre
-- refuse ensuite ce jeton, sans toucher aux autres appareils de l'utilisateur. La ligne n'est utile que jusqu'à
-- l'expiration naturelle du jeton (expires_at) ; les lignes expirées sont purgées au fil de l'eau.
CREATE TABLE revoked_token (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_revoked_token_expires_at ON revoked_token (expires_at);
