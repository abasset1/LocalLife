package com.locallife.backend.activity.api;

import com.locallife.backend.activity.domain.Activity;
import java.time.LocalDateTime;

/**
 * Représentation d'une activité pour les recherches publiques
 * ({@code /nearby}, {@code /within-bounds} — LL-8006).
 *
 * Remplace {@code sourceId} (identifiant technique de {@link Activity},
 * sans valeur pour l'utilisateur final) par {@code sourceName}, le nom
 * lisible de la {@code Source} d'origine (ex. {@code "OpenAgenda —
 * Avignon"}, ou {@code "MANUAL"} pour une contribution) — critère
 * d'acceptation LL-8006 : « valider que les informations clés (titre,
 * date, lieu, source) sont correctement affichées ». Avant ce ticket,
 * le frontend n'avait accès qu'à {@code sourceId}, inexploitable
 * directement pour l'affichage (voir {@code SPRINT_8.md}, constat fait en
 * vérifiant l'affichage bout en bout des activités sur la carte).
 *
 * {@code latitude}/{@code longitude} restent tels quels (aucune adresse
 * texte n'est stockée en base, voir la javadoc de
 * {@link ActivityController.CreateActivityRequest}) : ce sont les seules
 * données de localisation disponibles, utilisées par le frontend comme
 * « lieu » affiché dans le popup de la carte.
 *
 * Uniquement utilisée par {@code getNearbyActivities}/
 * {@code getActivitiesWithinBounds} (les deux endpoints qui alimentent la
 * carte) : {@code getAllActivities}/{@code getActivityById} continuent de
 * renvoyer {@link Activity} tel quel, hors périmètre de ce ticket
 * (aucun consommateur actuel n'a besoin de {@code sourceName} sur ces
 * deux endpoints).
 */
public record ActivityResponse(
        Long id,
        String title,
        String description,
        String category,
        double latitude,
        double longitude,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        String sourceName,
        String url) {

    public static ActivityResponse from(Activity activity, String sourceName) {
        return new ActivityResponse(
                activity.id(),
                activity.title(),
                activity.description(),
                activity.category(),
                activity.latitude(),
                activity.longitude(),
                activity.startDate(),
                activity.endDate(),
                activity.status(),
                sourceName,
                activity.url());
    }
}
