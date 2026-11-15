-- activity_photo : image ajoutée par le contributeur depuis le formulaire « Proposer une activité » (site et
-- application). Une image par activité, stockée en base comme les photos de food truck (V84), servie par
-- GET /api/v1/activities/{id}/photo. Elle passe avant les médias importés comme image principale.

CREATE TABLE activity_photo (
    activity_id BIGINT PRIMARY KEY REFERENCES activity (id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL,
    data BYTEA NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
