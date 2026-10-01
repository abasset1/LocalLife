package com.locallife.backend.organizertype.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;

/**
 * Entité de domaine OrganizerType — référentiel canonique des 9 types
 * d'organisateurs V1 (CAT-004, {@code docs/05_Sprints/SPRINT_CATEG.md}).
 *
 * Règle du ticket : « un organisateur n'est jamais une catégorie »
 * (ex. {@code ASSOCIATION} ne signifie pas que l'activité appartient à
 * une catégorie "association"). Cette entité ne porte donc aucune
 * référence vers {@code Category}, {@code ActivityType} ni
 * {@code Audience} — référentiel indépendant des trois précédents.
 *
 * À ne pas confondre avec {@link com.locallife.backend.source.domain.Source}
 * ({@code type} = {@code API}/{@code RSS}/{@code MANUAL}) : {@code Source}
 * qualifie le canal technique par lequel une activité a été importée,
 * {@code OrganizerType} qualifie qui organise l'activité (une dimension
 * métier distincte, absente du code avant CAT-004 — confirmé par l'audit
 * CAT-000, {@code docs/CATEGORY_MIGRATION_MAP.md} §2.5).
 *
 * Critère explicite du ticket, propre à CAT-004 : « aucune logique métier
 * ne dépend du label ». {@link
 * com.locallife.backend.organizertype.application.OrganizerTypeService} ne
 * compare donc jamais de label, uniquement {@code code}.
 *
 * Même structure ID/CODE/LABEL que les référentiels précédents. Les 9
 * types V1 sont créés une fois pour toutes par
 * {@code V35__create_organizer_type_referential.sql}.
 */
public record OrganizerType(
        @Id Long id,
        String code,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @MappedCollection(idColumn = "organizer_type_id") List<OrganizerTypeTranslation> translations) {
}
