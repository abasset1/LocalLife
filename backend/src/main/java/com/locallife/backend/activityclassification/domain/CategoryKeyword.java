package com.locallife.backend.activityclassification.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Mot-clé du référentiel de classification — voir {@code V39__create_category_keyword.sql}. Le mot-clé est
 * stocké tel quel (accents compris) ; il référence une catégorie existante. Depuis l'issue #50 c'est le seul
 * référentiel : il sert aux titres et aux catégories d'origine / tags de la source.
 *
 * @param scope {@link #SCOPE_ALL} (titres et sources) ou {@link #SCOPE_SOURCE} (catégories d'origine et tags
 *              seulement : terme fiable dans les mots-clés structurés d'un agenda, trop ambigu dans un titre
 *              libre) — {@code V45__unify_keyword_referential.sql}
 */
public record CategoryKeyword(
        @Id Long id,
        Long categoryId,
        String keyword,
        String scope,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_SOURCE = "SOURCE";
}
