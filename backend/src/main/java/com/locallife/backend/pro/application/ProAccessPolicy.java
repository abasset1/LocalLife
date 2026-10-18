package com.locallife.backend.pro.application;

/**
 * Point d'extension unique de la question « ce compte a-t-il accès à l'espace professionnel ? » (ADR-0012, décision
 * D1), sur le modèle de {@code PremiumAccessPolicy} (ADR-0008). Les services de l'espace professionnel ne
 * connaissent que cette interface : BILLING-01 pourra la rattacher aux abonnements sans les modifier.
 */
public interface ProAccessPolicy {

    boolean hasProAccess(Long userId);
}
