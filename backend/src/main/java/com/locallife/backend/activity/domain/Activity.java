package com.locallife.backend.activity.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Activity.
 *
 * {@code sourceId} et {@code importKey} ajoutés en LL-5008 : lien vers la
 * {@code Source} d'origine (voir {@code source} module), obligatoire pour
 * toute activité — y compris les créations manuelles, rattachées à la
 * source réservée {@code MANUAL} (voir {@code SOURCE_CONTRACT.md} et
 * {@code ActivityService#createActivity}). {@code importKey} est la clé
 * de déduplication calculée par {@code DeduplicationService} (LL-5007) ;
 * {@code null} pour les activités créées manuellement (aucune donnée
 * collectée à déduplicer), non nul pour les activités importées, unique
 * par {@code sourceId} (voir {@code V9__link_activity_to_source.sql}).
 *
 * {@code url} ajouté en LL-6002 (Sprint 6, audit LL-6001) : reprend
 * {@code CollectedActivity#sourceUrl}, jusqu'ici collecté par
 * {@code OpenAgendaCollector} puis perdu à la normalisation faute de
 * champ pour le porter — voir {@code DATA_QUALITY_AUDIT.md}. Toujours
 * {@code null} pour une activité créée manuellement (le formulaire de
 * contribution ne demande pas d'URL, hors périmètre de ce ticket).
 *
 * {@code status} formalisé en LL-6003 (Sprint 6) : trois valeurs MVP,
 * imposées depuis la migration {@code V11__enforce_activity_status.sql}
 * par une contrainte {@code CHECK} en base (défaut applicatif déjà en
 * place avant cette contrainte, voir {@code ActivityService#createActivity}
 * et {@code NormalizationService}) —
 * <ul>
 *   <li>{@code PENDING} : valeur par défaut d'une contribution manuelle
 *       (en attente de modération) ;</li>
 *   <li>{@code PUBLISHED} : valeur par défaut d'une activité importée
 *       (source jugée fiable, voir {@code NormalizationService}), et
 *       seule valeur qu'une activité {@code PENDING} peut atteindre après
 *       validation par un administrateur ;</li>
 *   <li>{@code REJECTED} : atteinte depuis {@code PENDING} après rejet
 *       par un administrateur.</li>
 * </ul>
 * Transitions volontairement minimales (pas de machine à états
 * complexe, critère d'acceptation explicite de LL-6003) : seules
 * {@code PENDING → PUBLISHED} et {@code PENDING → REJECTED} sont
 * prévues ; aucun retour en arrière, aucune transition depuis
 * {@code PUBLISHED} ou {@code REJECTED}. Aucun endpoint ne permet
 * encore de déclencher ces transitions à ce stade (prévu en LL-6006) ;
 * ce ticket ne fait que définir et persister les valeurs possibles.
 * Rien ne change encore côté visibilité publique : l'exclusion des
 * activités non {@code PUBLISHED} des recherches publiques est le
 * périmètre de LL-6004, pas de celui-ci.
 */
public record Activity(
        @Id Long id,
        String title,
        String description,
        String category,
        double latitude,
        double longitude,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        Long sourceId,
        String importKey,
        String url) {
}
