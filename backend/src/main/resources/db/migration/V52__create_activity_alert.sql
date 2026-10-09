-- Alertes email : un utilisateur reçoit par email les nouvelles activités
-- publiées qui correspondent à une ou plusieurs catégories, dans un rayon
-- autour d'une ville.
--
-- min_activity_id : plus grand activity.id existant à la création de
-- l'alerte. activity n'a pas de date de création : seules les activités
-- d'id supérieur sont « nouvelles » pour cette alerte (pas d'envoi de tout
-- le stock existant au premier passage).
--
-- unsubscribe_token : jeton aléatoire du lien de désinscription présent
-- dans chaque email, utilisable sans être connecté. Il ne permet que de
-- supprimer cette alerte.
CREATE TABLE activity_alert (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    city              VARCHAR(255)     NOT NULL,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    radius_km         INTEGER          NOT NULL CHECK (radius_km BETWEEN 1 AND 50),
    min_activity_id   BIGINT           NOT NULL,
    unsubscribe_token VARCHAR(64)      NOT NULL UNIQUE,
    created_at        TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE INDEX idx_activity_alert_user_id ON activity_alert(user_id);

CREATE TABLE activity_alert_category (
    alert_id      BIGINT      NOT NULL REFERENCES activity_alert(id) ON DELETE CASCADE,
    category_code VARCHAR(50) NOT NULL REFERENCES category(code) ON DELETE CASCADE,
    PRIMARY KEY (alert_id, category_code)
);

-- Activités déjà envoyées pour une alerte : une activité n'est jamais
-- envoyée deux fois, même si elle est republiée ou modifiée.
CREATE TABLE activity_alert_sent (
    alert_id    BIGINT    NOT NULL REFERENCES activity_alert(id) ON DELETE CASCADE,
    activity_id BIGINT    NOT NULL REFERENCES activity(id) ON DELETE CASCADE,
    sent_at     TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (alert_id, activity_id)
);
