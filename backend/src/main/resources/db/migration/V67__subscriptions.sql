-- BILLING-01 (#142, ADR-0014, décisions du 19/10/2026) : abonnements Premium et Pro, sans paiement réel.
--
-- Un utilisateur demande une offre ; un administrateur active ou refuse la demande. Le modèle porte déjà ce qu'un
-- prestataire de paiement remplira plus tard (provider, identifiants client et abonnement) ; aucun prix n'est stocké.
-- La consultation de la carte et de la liste reste gratuite quel que soit l'abonnement.
--
-- status : REQUESTED (en attente d'un administrateur), ACTIVE, CANCELED (résilié : l'accès dure jusqu'à
-- current_period_end), EXPIRED (période terminée), REJECTED (demande refusée), WITHDRAWN (demande retirée),
-- REPLACED (remplacé par une autre offre).

CREATE TABLE subscription (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan VARCHAR(20) NOT NULL CHECK (plan IN ('PREMIUM', 'PRO')),
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('REQUESTED', 'ACTIVE', 'CANCELED', 'EXPIRED', 'REJECTED', 'WITHDRAWN', 'REPLACED')),
    current_period_start TIMESTAMPTZ,
    -- NULL : sans échéance (activation manuelle sans durée).
    current_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    provider_customer_id VARCHAR(255),
    provider_subscription_id VARCHAR(255),
    decided_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    decision_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Au plus une demande en attente et un abonnement en cours (actif ou résilié non échu) par utilisateur.
CREATE UNIQUE INDEX uq_subscription_requested ON subscription (user_id) WHERE status = 'REQUESTED';
CREATE UNIQUE INDEX uq_subscription_current ON subscription (user_id) WHERE status IN ('ACTIVE', 'CANCELED');
CREATE INDEX idx_subscription_status ON subscription (status, created_at);

-- Journal des changements d'abonnement (BILLING-01, point 9) : jamais modifié.
CREATE TABLE subscription_event (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscription (id) ON DELETE CASCADE,
    actor_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_event_subscription ON subscription_event (subscription_id, id);
