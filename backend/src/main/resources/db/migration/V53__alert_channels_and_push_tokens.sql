-- Alertes en notification push (application mobile).
--
-- Chaque alerte choisit ses canaux : e-mail (comportement existant, conservé
-- par défaut pour les alertes déjà créées) et/ou notification push. Au moins
-- un canal doit rester actif.
ALTER TABLE activity_alert ADD COLUMN notify_email BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE activity_alert ADD COLUMN notify_push BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE activity_alert ADD CONSTRAINT chk_activity_alert_channel CHECK (notify_email OR notify_push);

-- Jetons Expo Push des appareils de l'utilisateur (un par installation de
-- l'application). Un jeton n'appartient qu'à un utilisateur à la fois : se
-- connecter avec un autre compte sur le même téléphone le réattribue.
-- Supprimé à la déconnexion, ou quand Expo signale que l'appareil n'est plus
-- enregistré (DeviceNotRegistered).
CREATE TABLE user_push_token (
    token        VARCHAR(255) PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform     VARCHAR(20),
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_push_token_user_id ON user_push_token(user_id);
