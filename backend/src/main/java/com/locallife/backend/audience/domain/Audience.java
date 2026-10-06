package com.locallife.backend.audience.domain;

import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;

/**
 * Entité de domaine Audience — référentiel canonique des 7 publics V1
 * (CAT-003, {@code docs/05_Sprints/SPRINT_CATEG.md}).
 *
 * Règle du ticket : « FAMILY est un public, ce n'est jamais une
 * catégorie ». Cette entité ne porte donc aucune référence vers
 * {@link com.locallife.backend.category.domain.Category} ni vers
 * {@link com.locallife.backend.activitytype.domain.ActivityType} — c'est
 * un référentiel indépendant, au même titre que ces deux-là.
 *
 * Particularité par rapport à {@code Category}/{@code ActivityType} (à
 * retenir pour les tickets suivants) : les exemples du sprint
 * ({@code AUDIENCE = CHILDREN, FAMILY}) montrent qu'une activité peut
 * porter plusieurs publics à la fois, alors qu'elle ne porte qu'une seule
 * catégorie et qu'un seul type. CAT-003 ne crée que le référentiel ; la
 * relation many-to-many activité ↔ publics reste à construire par un
 * ticket ultérieur.
 *
 * Même structure ID/CODE/LABEL que les référentiels précédents : {@code id}
 * technique jamais exposé en API, {@code code} stable en
 * {@code SCREAMING_SNAKE_CASE}, label/description portés par
 * {@link AudienceTranslation}. Les 7 publics V1 sont créés une fois pour
 * toutes par {@code V34__create_audience_referential.sql}.
 */
public record Audience(
        @Id Long id,
        String code,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @MappedCollection(idColumn = "audience_id") Set<AudienceTranslation> translations) {
}
