package com.locallife.backend.category.domain;

import org.springframework.data.annotation.Id;

/**
 * Traduction d'une {@link Category} pour une locale donnée (CAT-001/CAT-005,
 * SPRINT_CATEG). Le label et la description affichés à l'utilisateur ne
 * sont jamais stockés dans {@link Category} elle-même, pour permettre
 * d'ajouter des locales sans modification de schéma (V1 : {@code fr-FR}
 * uniquement — voir {@code docs/05_Sprints/SPRINT_CATEG.md}, section
 * "Stratégie de traduction").
 *
 * Entité enfant d'un agrégat Spring Data JDBC : persistée/chargée via
 * {@link Category#translations()} ({@code @MappedCollection}), jamais
 * accédée indépendamment.
 */
public record CategoryTranslation(
        @Id Long id,
        String locale,
        String label,
        String description) {
}
