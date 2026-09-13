package com.locallife.backend.collector.domain;

import java.util.List;

/**
 * Modèle interne représentant une donnée collectée avant conversion vers
 * {@link com.locallife.backend.activity.domain.Activity} (LL-5004). C'est
 * le type de donnée « brute normalisable » retourné par un
 * {@code Collector} (voir {@code COLLECTOR_CONTRACT.md}, LL-5003).
 *
 * Simple porteur de données, comme {@code Activity} et {@code Source} :
 * aucune validation ici. La conversion vers {@code Activity} (et les
 * autres entités du modèle événementiel riche, voir plus bas) ainsi que
 * le rejet des données invalides relèvent du pipeline de normalisation
 * (LL-5005/LL-11011), pas de ce modèle.
 *
 * <b>Refonte LL-11011</b> (« adapter le pipeline d'import ») : jusqu'ici
 * ce modèle portait directement {@code latitude}/{@code longitude}/
 * {@code address}/{@code city}/{@code postalCode} (LL-EF-008) et un
 * unique couple {@code startDate}/{@code endDate} (LL-5004). Le flux
 * cible du ticket (Collector → ExternalActivity → Normalisation →
 * Validation → Persistence → Activity + Location + Schedule + Occurrence
 * + Media + Links + Accessibility + Tags + Metadata) exige de porter des
 * données plus riches, désormais regroupées en sous-modèles dédiés
 * (LL-11011) plutôt que d'ajouter encore des champs plats :
 * <ul>
 *   <li>{@link CollectedLocation} {@code location} remplace {@code
 *       latitude}/{@code longitude}/{@code address}/{@code city}/{@code
 *       postalCode} — localisation structurée (critère d'acceptation
 *       explicite du ticket) ;</li>
 *   <li>{@link CollectedTiming} {@code timings} (liste) remplace
 *       l'unique couple {@code startDate}/{@code endDate} — plusieurs
 *       horaires supportés (critère d'acceptation explicite) ;</li>
 *   <li>{@code recurrenceRule} (RRULE RFC 5545, voir {@code
 *       RecurrenceRuleService}, LL-11003) : porté ici pour une source
 *       qui exposerait une règle de récurrence explicite plutôt que des
 *       occurrences déjà « à plat » — voir la javadoc d'{@code
 *       OpenAgendaCollector} pour la raison pour laquelle ce collecteur
 *       ne le renseigne jamais lui-même (ses horaires sont déjà
 *       expansés par la source) ;</li>
 *   <li>{@link CollectedMedia} {@code media} (liste), {@link
 *       CollectedLink} {@code links} (liste), {@link
 *       CollectedAccessibility} {@code accessibility}, {@code tags}
 *       (liste) et {@code registrationInfo} : couvrent le reste du
 *       « flux cible » du ticket (médias, liens, accessibilité, tags,
 *       informations d'inscription).</li>
 * </ul>
 * Tous nullables/vides par défaut selon la source (même tolérance aux
 * données incomplètes qu'ailleurs dans le projet) — voir {@code
 * NormalizationService} pour le détail de ce qui est effectivement
 * persisté à partir de chacun.
 *
 * {@code source} identifie la source par son nom (voir la décision
 * {@code getSourceName()} de {@code COLLECTOR_CONTRACT.md}), pas par un
 * identifiant technique — ce modèle n'a pas connaissance de la
 * persistance.
 */
public record CollectedActivity(
        String title,
        String description,
        String category,
        String sourceUrl,
        String externalId,
        String source,
        String longDescription,
        String conditions,
        Integer ageMin,
        Integer ageMax,
        CollectedLocation location,
        List<CollectedTiming> timings,
        String recurrenceRule,
        List<CollectedMedia> media,
        List<CollectedLink> links,
        CollectedAccessibility accessibility,
        List<String> tags,
        String registrationInfo) {
}
