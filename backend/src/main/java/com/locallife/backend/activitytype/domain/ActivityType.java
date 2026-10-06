package com.locallife.backend.activitytype.domain;

import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;

/**
 * Entité de domaine ActivityType — référentiel canonique des 38 types
 * d'activités V1 (CAT-002, {@code docs/05_Sprints/SPRINT_CATEG.md}).
 *
 * Objectif du ticket : « séparer clairement la nature de l'activité de sa
 * catégorie ». Un type ({@code FESTIVAL}, {@code ATELIER}, {@code MARCHE}...)
 * n'est donc **jamais** utilisé comme catégorie et inversement — cette
 * entité ne porte aucune référence vers {@link
 * com.locallife.backend.category.domain.Category} ; le rattachement d'une
 * activité à un type ET à une catégorie est une décision de classification
 * qui relève des tickets suivants du sprint, pas de CAT-002.
 *
 * Même structure ID/CODE/LABEL que {@code Category} (CAT-001) :
 * <ul>
 *   <li>{@code id} : identifiant technique interne, jamais exposé en API ;</li>
 *   <li>{@code code} : identité métier stable, {@code SCREAMING_SNAKE_CASE}
 *       (ex. {@code FESTIVAL}) ;</li>
 *   <li>{@code label}/{@code description} : portés par
 *       {@link ActivityTypeTranslation}, une ligne par locale.</li>
 * </ul>
 *
 * Les 38 types V1 sont créés une fois pour toutes par la migration
 * {@code V33__create_activity_type_referential.sql} — aucun type n'est créé
 * automatiquement à partir des valeurs observées dans les imports.
 */
public record ActivityType(
        @Id Long id,
        String code,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @MappedCollection(idColumn = "activity_type_id") Set<ActivityTypeTranslation> translations) {
}
