-- Avis aux contacts des activités importées (ImportedActivityContactNotifier) : l'adresse e-mail de contact d'une
-- activité récupérée chez une source externe reçoit un message indiquant que LocalLife référence l'événement, et
-- qu'un compte professionnel est nécessaire pour qu'il continue de l'être. Une ligne par activité et par adresse
-- prévenue : jamais deux fois le même avis pour la même activité, même après un nouvel import (les liens de
-- l'activité sont alors recréés, l'activité garde son identifiant).
CREATE TABLE imported_activity_contact_notice (
    activity_id BIGINT NOT NULL REFERENCES activity(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (activity_id, email)
);

-- Délai minimal entre deux avis à une même adresse.
CREATE INDEX idx_imported_activity_contact_notice_email_sent_at
    ON imported_activity_contact_notice (email, sent_at);
