package com.locallife.backend.premium.application;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Implémentation provisoire de {@link PremiumAccessPolicy} (LL-11014).
 *
 * Aucun système d'abonnement Premium n'existe encore (paiement,
 * abonnement et facturation sont explicitement hors périmètre du ticket,
 * voir sa javadoc de classe) — cette implémentation accorde donc l'accès
 * complet à tout le monde ({@code true} inconditionnel), pour ne rien
 * changer au comportement observable de {@code GET
 * /api/v1/activities/{id}} par rapport à LL-11013 (fiche détaillée pour
 * tout le monde) tant qu'une véritable autorisation Premium n'existe
 * pas.
 *
 * ⚠️ Décision à valider, provisoire par construction : à remplacer par
 * une implémentation consultant un futur système d'abonnement (hors
 * périmètre de ce ticket) le jour où l'un sera développé — {@code
 * authentication} n'est pour l'instant pas exploité, mais fait déjà
 * partie de la signature de {@link PremiumAccessPolicy} pour ce jour-là
 * (voir sa javadoc).
 */
@Service
public class StubPremiumAccessPolicy implements PremiumAccessPolicy {

    @Override
    public boolean hasPremiumAccess(Authentication authentication) {
        return true;
    }
}
