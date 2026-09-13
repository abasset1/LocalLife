package com.locallife.backend.collector.domain;

/**
 * Localisation structurée d'une donnée collectée (LL-11011,
 * {@code docs/05_Sprints/SPRINT_11.md}, section 14 : « localisation
 * structurée »). Remplace les champs plats {@code latitude}/{@code
 * longitude}/{@code address}/{@code city}/{@code postalCode} qu'exposait
 * directement {@code CollectedActivity} avant ce ticket (LL-EF-008) —
 * regroupés ici pour porter aussi {@code region}/{@code timezone},
 * nécessaires à {@code NormalizationService#normalizeSchedule} (LL-11002)
 * sans élargir davantage {@code CollectedActivity} lui-même.
 *
 * Tous les champs restent nullables (même tolérance aux données
 * incomplètes qu'ailleurs dans le projet, voir {@code
 * Location}) : une source peut ne fournir que des coordonnées, ou au
 * contraire une adresse sans coordonnées résolues.
 */
public record CollectedLocation(
        String name,
        String address,
        String city,
        String postalCode,
        String region,
        String countryCode,
        Double latitude,
        Double longitude,
        String timezone) {
}
