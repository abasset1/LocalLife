package com.locallife.backend.category.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;

/**
 * Entité de domaine Category — référentiel canonique des catégories V1
 * (CAT-001, {@code docs/05_Sprints/SPRINT_CATEG.md}). Remplace le modèle
 * {@code (id, name, description)} introduit par V5, vide et orphelin
 * (voir {@code docs/CATEGORY_MIGRATION_MAP.md} §1 — aucune donnée, aucune
 * FK depuis {@code activity}, aucun code applicatif ne l'alimentait).
 *
 * Suit la règle ID/CODE/LABEL du sprint :
 * <ul>
 *   <li>{@code id} : identifiant technique interne, jamais exposé en API
 *       (voir {@code category.api}, qui expose {@code code} à la place) ;</li>
 *   <li>{@code code} : identité métier stable, {@code SCREAMING_SNAKE_CASE}
 *       (ex. {@code CULTURE}), indépendante de la langue, de la source et
 *       de l'ID — c'est la seule valeur à utiliser dans toute logique
 *       métier ;</li>
 *   <li>{@code label}/{@code description} : jamais sur cette entité, portés
 *       par {@link CategoryTranslation} (une ligne par locale).</li>
 * </ul>
 *
 * Les 9 catégories V1 sont créées une fois pour toutes par la migration
 * {@code V32__refactor_category_referential.sql} : conformément à CAT-001,
 * aucune catégorie n'est créée automatiquement à partir des valeurs
 * observées dans les sources importées (contrairement à {@code Source} ou
 * {@code activity_tag}).
 *
 * Le rattachement d'une {@code Activity} à une {@code Category} (ajout
 * d'une FK {@code category_id} sur {@code activity}) est hors périmètre de
 * CAT-001 — prévu par CAT-002 (référentiel des types) et les tickets
 * suivants du sprint, pas par ce ticket.
 */
public record Category(
        @Id Long id,
        String code,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @MappedCollection(idColumn = "category_id") List<CategoryTranslation> translations) {
}
