-- Limitation du login par IP, en plus de par adresse e-mail (correctif sécurité du 06/11/2026) : contre le
-- « credential stuffing », où un attaquant essaie une fois chacune de très nombreuses adresses depuis une même
-- machine (la limite par e-mail ne se déclenche alors jamais).
--
-- On retient l'empreinte HMAC-SHA256 de l'IP cliente, jamais l'IP en clair (RGPD, même posture que email_hash).
-- Nullable : les lignes déjà présentes, et les échecs sans IP exploitable (boucle locale, appels internes), ne
-- portent pas d'empreinte d'IP et restent valides.
ALTER TABLE login_failure ADD COLUMN ip_hash VARCHAR(64);

CREATE INDEX idx_login_failure_ip ON login_failure (ip_hash, occurred_at DESC);
