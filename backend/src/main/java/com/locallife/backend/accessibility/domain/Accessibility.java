package com.locallife.backend.accessibility.domain;

import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Accessibility (LL-11009, Sprint 11, section 12) :
 * modèle structuré des principales informations d'accessibilité d'une
 * activité — critère d'acceptation explicite « informations importantes
 * structurées » (à l'opposé de {@code ActivityMetadata}, JSONB, pour ce
 * qui n'est pas encore jugé assez important pour mériter une structure
 * dédiée).
 *
 * Cinq catégories de handicap, reprises d'une catégorisation reconnue
 * et déjà utilisée par plusieurs sources d'événements (dont OpenAgenda,
 * voir {@code developers.openagenda.com/evenements/structure/} — non
 * vérifié contre l'API réelle en sandbox, recherche web uniquement,
 * comme pour LL-11006) : moteur, auditif, visuel, psychique,
 * intellectuel. Champs nommés en toutes lettres plutôt que les codes
 * courts de la source ({@code mi}/{@code hi}/{@code vi}/{@code pi}/
 * {@code ii}) pour rester lisible indépendamment de la source, même
 * principe que LL-11006 (« aucune dépendance au modèle OpenAgenda dans
 * le domaine »).
 *
 * Chaque champ en {@link Boolean} (objet, pas primitif) et nullable :
 * {@code null} signifie « non renseigné par la source », distinct de
 * {@code false} (« source affirmant explicitement que ce n'est pas
 * accessible ») — cette distinction serait perdue avec un booléen
 * primitif.
 *
 * Relation un-à-un avec {@code Activity} (table séparée, {@code
 * activity_id} unique — voir
 * {@code V24__create_accessibility_tag_metadata_tables.sql}) plutôt que
 * des colonnes directement sur {@code Activity} : même raisonnement que
 * {@code ActivityMetadata}, évite d'alourdir davantage son constructeur
 * (déjà 19 champs après LL-11006).
 */
public record Accessibility(
        @Id Long id,
        Long activityId,
        Boolean motorImpairment,
        Boolean hearingImpairment,
        Boolean visualImpairment,
        Boolean psychicImpairment,
        Boolean intellectualImpairment) {
}
