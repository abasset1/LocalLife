-- Suggestions et anomalies envoyées par les utilisateurs connectés depuis le site (formulaire « Une idée, un
-- problème ? »), consultées par les administrateurs (Administration → Retours). Chaque nouveau message prévient les
-- administrateurs par e-mail.
--
-- kind : SUGGESTION ou BUG ; page_url : page d'où le message a été envoyé (aide à reproduire une anomalie) ;
-- status : NEW tant qu'un administrateur ne l'a pas marqué traité (DONE), réversible.
-- Supprimé avec le compte de son auteur (droit à l'effacement) ; inclus dans l'export des données.
CREATE TABLE feedback (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        VARCHAR(20)   NOT NULL CHECK (kind IN ('SUGGESTION', 'BUG')),
    message     VARCHAR(2000) NOT NULL,
    page_url    VARCHAR(500),
    user_agent  VARCHAR(300),
    status      VARCHAR(20)   NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'DONE')),
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    handled_at  TIMESTAMP,
    handled_by  BIGINT        REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX feedback_status_created_idx ON feedback (status, created_at DESC);
CREATE INDEX feedback_user_idx ON feedback (user_id);
