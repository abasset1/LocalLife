package com.locallife.backend.premium.application;

import org.springframework.security.core.Authentication;

/**
 * Point d'extension unique décidant si l'appelant doit recevoir la fiche
 * détaillée d'une activité ou seulement son résumé (LL-11014, « préparer
 * la fiche Premium »).
 *
 * Règle fondamentale du ticket (`SPRINT_11.md`, section 17) : « le modèle
 * de données ne doit pas connaître le forfait ». Aucune entité de domaine
 * (`Activity`, `User`, etc.) ne porte de champ lié au Premium — c'est
 * cette interface, seule, qui porte la décision, à la frontière API
 * (voir {@code ActivityController#getActivityById}) :
 *
 * <pre>
 * Utilisateur gratuit  → Résumé (ActivitySummaryResponse)
 * Utilisateur Premium  → Fiche détaillée (ActivityDetailResponse)
 * </pre>
 *
 * Paiement, abonnement, facturation et gestion commerciale Premium sont
 * explicitement hors périmètre de ce ticket (voir sa section « Hors
 * périmètre ») — aucune implémentation ici ne sait donc *comment* un
 * utilisateur devient Premium, seulement *où* la question est posée.
 * Voir {@link StubPremiumAccessPolicy} pour l'implémentation actuelle
 * (provisoire) et {@code docs/ADR-0008-preparation-premium.md} pour le
 * détail de cette décision de conception.
 */
public interface PremiumAccessPolicy {

    /**
     * @param authentication l'authentification de l'appelant, telle que
     *     fournie par Spring Security — jamais {@code null} sur un
     *     endpoint public (authentification anonyme non désactivée dans
     *     {@code SecurityConfig}, voir {@code
     *     ActivityController#withSourceNames}), mais pas forcément une
     *     {@code JwtAuthentication} (utilisateur non connecté).
     * @return {@code true} si l'appelant doit recevoir la fiche
     *     détaillée, {@code false} s'il doit recevoir le résumé.
     */
    boolean hasPremiumAccess(Authentication authentication);
}
