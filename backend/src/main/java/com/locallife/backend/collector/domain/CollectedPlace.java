package com.locallife.backend.collector.domain;

import java.util.Set;

/**
 * Lieu collecté (ADR-0011, phase B) : une ligne « lieu » d'une source, avant liste blanche et modération.
 * {@code types} : tous les types de la source (noms locaux DATAtourisme, parents compris).
 */
public record CollectedPlace(
        String externalId,
        String name,
        Set<String> types,
        String address,
        String postalCode,
        String city,
        Double latitude,
        Double longitude,
        String description,
        String website) {
}
