package com.locallife.backend.activity.api;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityHostPlaceRepository.HostPlace;
import com.locallife.backend.place.domain.CoordinatePrecision;
import java.time.LocalDateTime;

/**
 * Représentation légère d'une activité pour {@code GET
 * /api/v1/activities} (LL-11013, « séparer les données nécessaires à la
 * carte/liste de celles nécessaires à la fiche détaillée »). Exactement
 * les champs listés par le ticket (« doit notamment pouvoir fournir ») :
 * {@code id}/{@code title}/{@code category}/date pertinente ({@code
 * date}, reprise de {@code Activity#startDate})/ville/adresse/
 * coordonnées/image principale.
 *
 * Remplace l'exposition directe de {@link Activity} sur cet endpoint
 * (avant ce ticket, {@code GET /api/v1/activities} renvoyait l'entité de
 * domaine telle quelle — voir l'ancien {@code
 * ActivityController#getAllActivities}) : évitait déjà un couplage
 * direct au schéma de la table {@code activity}, mais exposait aussi des
 * champs internes sans utilité pour une liste (ex. {@code sourceId},
 * {@code importKey}, {@code conditions}, {@code ageMin}/{@code ageMax},
 * {@code longDescription}) — répond au critère d'acceptation « aucune
 * donnée sensible exposée ».
 *
 * Volontairement distinct d'{@code ActivityResponse} (utilisé par
 * {@code /nearby}/{@code /within-bounds}/les activités likées) plutôt
 * que de le réutiliser ou de l'étendre : ce dernier porte des champs
 * délibérément ajoutés pour des besoins concrets de ces trois endpoints
 * (voir sa javadoc — {@code likedByCurrentUser}, {@code
 * createdByUsername}, etc.), hors périmètre de ce ticket et non listés
 * par lui pour {@code GET /api/v1/activities} — les dupliquer ici
 * aurait recréé le problème que ce ticket cherche justement à résoudre
 * (payload plus lourd que nécessaire pour une simple liste/carte).
 *
 * {@code mainImageUrl} : url du premier {@code Media} attaché à
 * l'activité (position 0, voir {@code ActivityMediaService
 * #findByActivityId}, ordre déjà garanti par LL-11007), ou {@code null}
 * si aucun média n'est attaché — jamais d'image par défaut/générique
 * injectée ici, à la charge du frontend le cas échéant.
 *
 * {@code createdBy} : identifiant du contributeur ({@code null} pour une activité importée), voir
 * {@link ActivityDetailResponse}.
 */
public record ActivitySummaryResponse(
        Long id,
        String title,
        String category,
        LocalDateTime date,
        String city,
        String address,
        Double latitude,
        Double longitude,
        String mainImageUrl,
        Long createdBy,
        String coordinatePrecision,
        HostPlace hostPlace) {

    public static ActivitySummaryResponse from(Activity activity, String mainImageUrl) {
        return new ActivitySummaryResponse(
                activity.id(),
                activity.title(),
                activity.category(),
                activity.startDate(),
                activity.city(),
                activity.address(),
                activity.latitude(),
                activity.longitude(),
                mainImageUrl,
                activity.createdBy(),
                CoordinatePrecision.UNKNOWN.name(),
                null);
    }

    /** Précision géographique de la position (ADR-0011, phase A) ; {@code from} vaut {@code UNKNOWN}. */
    public ActivitySummaryResponse withCoordinatePrecision(CoordinatePrecision precision) {
        return new ActivitySummaryResponse(id, title, category, date, city, address, latitude, longitude,
                mainImageUrl, createdBy, (precision == null ? CoordinatePrecision.UNKNOWN : precision).name(),
                hostPlace);
    }

    /** Lieu qui propose l'activité (décision du 22/10/2026), {@code null} sinon. */
    public ActivitySummaryResponse withHostPlace(HostPlace place) {
        return new ActivitySummaryResponse(id, title, category, date, city, address, latitude, longitude,
                mainImageUrl, createdBy, coordinatePrecision, place);
    }
}
