package com.locallife.backend.collector.domain;

/**
 * Informations d'accessibilité collectées (LL-11011, {@code
 * SPRINT_11.md}, section 14 : « accessibilité »). Mêmes cinq catégories
 * que {@link com.locallife.backend.accessibility.domain.Accessibility}
 * (moteur, auditif, visuel, psychique, intellectuel) — voir sa javadoc.
 * Chaque champ nullable et indépendant : {@code null} signifie
 * « non renseigné par la source », distinct de {@code false}.
 */
public record CollectedAccessibility(
        Boolean motorImpairment,
        Boolean hearingImpairment,
        Boolean visualImpairment,
        Boolean psychicImpairment,
        Boolean intellectualImpairment) {
}
