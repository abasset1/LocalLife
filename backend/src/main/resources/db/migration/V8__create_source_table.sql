CREATE TABLE source (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    url VARCHAR(512),
    status VARCHAR(50) NOT NULL,
    last_sync_at TIMESTAMP
);

-- Source réservée pour les activités créées manuellement (hors import),
-- décision documentée dans SOURCE_CONTRACT.md (LL-5001) : évite d'avoir à
-- traiter une source nulle dans le domaine métier.
INSERT INTO source (name, type, url, status, last_sync_at)
VALUES ('Saisie manuelle', 'MANUAL', NULL, 'ACTIVE', NULL);
