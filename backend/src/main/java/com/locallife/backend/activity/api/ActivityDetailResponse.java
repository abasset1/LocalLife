package com.locallife.backend.activity.api;

import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Représentation détaillée d'une activité pour {@code GET
 * /api/v1/activities/{id}} (LL-11013). Reprend les champs « sûrs »
 * d'{@link Activity} déjà exposés par {@code ActivityResponse} (voir sa
 * javadoc), complétés par l'arborescence riche du Sprint 11 explicitement
 * listée par le ticket : {@code schedules}, occurrences pertinentes,
 * {@code locations}, {@code media}, {@code links}, {@code accessibility},
 * {@code tags}, informations complémentaires.
 *
 * Remplace l'exposition directe de {@link Activity} sur cet endpoint
 * (avant ce ticket, {@code GET /api/v1/activities/{id}} renvoyait
 * l'entité de domaine telle quelle) — répond, comme {@link
 * ActivitySummaryResponse}, au critère d'acceptation « aucune donnée
 * sensible exposée » ({@code sourceId}/{@code importKey}, identifiants
 * techniques sans valeur pour un client, ne sont jamais exposés).
 *
 * ⚠️ Décision à valider : {@code sourceName}/{@code createdBy}/{@code
 * createdByUsername}/{@code likedByCurrentUser} (présents sur {@code
 * ActivityResponse}) ne sont volontairement pas repris ici — non listés
 * par l'arborescence du ticket pour cet endpoint, et leur résolution
 * (source/utilisateur/like courant) ajouterait des dépendances
 * supplémentaires à {@code ActivityController} pour un unique endpoint
 * de détail. Peuvent être ajoutés par un futur ticket si le frontend en
 * a besoin sur la fiche détaillée.
 *
 * {@code createdBy} : identifiant du contributeur ({@code null} pour une activité importée), déjà public
 * sur {@code ActivityResponse} — permet à l'app mobile de proposer « Modifier »/« Supprimer » à son
 * auteur sur la fiche (les droits restent vérifiés par le backend).
 */
public record ActivityDetailResponse(
        Long id,
        String title,
        String description,
        String longDescription,
        String category,
        Double latitude,
        Double longitude,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        String url,
        String address,
        String city,
        String postalCode,
        String conditions,
        Integer ageMin,
        Integer ageMax,
        List<ScheduleSummary> schedules,
        List<OccurrenceSummary> occurrences,
        List<LocationSummary> locations,
        List<MediaSummary> media,
        List<LinkSummary> links,
        AccessibilitySummary accessibility,
        List<String> tags,
        String additionalInfo,
        List<String> categoryCodes,
        LocalDateTime publishedAt,
        Long createdBy,
        String coordinatePrecision) {

    public static ActivityDetailResponse from(
            Activity activity,
            List<Schedule> schedules,
            List<Occurrence> occurrences,
            List<Location> locations,
            List<Media> media,
            List<ActivityLink> links,
            Accessibility accessibility,
            List<String> tags,
            String additionalInfo) {
        return from(activity, schedules, occurrences, locations, media, links, accessibility, tags,
                additionalInfo, List.of());
    }

    /** {@code categoryCodes} : codes des catégories du référentiel de l'activité (1..N). */
    public static ActivityDetailResponse from(
            Activity activity,
            List<Schedule> schedules,
            List<Occurrence> occurrences,
            List<Location> locations,
            List<Media> media,
            List<ActivityLink> links,
            Accessibility accessibility,
            List<String> tags,
            String additionalInfo,
            List<String> categoryCodes) {
        return new ActivityDetailResponse(
                activity.id(),
                activity.title(),
                activity.description(),
                activity.longDescription(),
                activity.category(),
                activity.latitude(),
                activity.longitude(),
                activity.startDate(),
                activity.endDate(),
                activity.status(),
                activity.url(),
                activity.address(),
                activity.city(),
                activity.postalCode(),
                activity.conditions(),
                activity.ageMin(),
                activity.ageMax(),
                schedules.stream().map(ScheduleSummary::from).toList(),
                occurrences.stream().map(OccurrenceSummary::from).toList(),
                locations.stream().map(LocationSummary::from).toList(),
                media.stream().map(MediaSummary::from).toList(),
                links.stream().map(LinkSummary::from).toList(),
                accessibility == null ? null : AccessibilitySummary.from(accessibility),
                tags,
                additionalInfo,
                categoryCodes,
                activity.publishedAt(),
                activity.createdBy(),
                CoordinatePrecision.UNKNOWN.name());
    }

    /** Précision géographique de la position affichée (ADR-0011, phase A) ; {@code from} vaut {@code UNKNOWN}. */
    public ActivityDetailResponse withCoordinatePrecision(CoordinatePrecision precision) {
        return new ActivityDetailResponse(id, title, description, longDescription, category, latitude, longitude,
                startDate, endDate, status, url, address, city, postalCode, conditions, ageMin, ageMax, schedules,
                occurrences, locations, media, links, accessibility, tags, additionalInfo, categoryCodes, publishedAt,
                createdBy, (precision == null ? CoordinatePrecision.UNKNOWN : precision).name());
    }

    /** Programmation d'une activité — voir {@link Schedule}. */
    public record ScheduleSummary(
            Long id,
            Long locationId,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate validFrom,
            LocalDate validUntil,
            String timezone,
            String recurrenceRule) {

        public static ScheduleSummary from(Schedule schedule) {
            return new ScheduleSummary(
                    schedule.id(), schedule.locationId(), schedule.startTime(), schedule.endTime(),
                    schedule.validFrom(), schedule.validUntil(), schedule.timezone(), schedule.recurrenceRule());
        }
    }

    /**
     * Une occurrence concrète — voir {@link Occurrence}. Toutes les
     * occurrences d'un schedule sont incluses, {@code CANCELLED}
     * comprises (revu en LL-11016, « Cas 4 — Exception » : une
     * occurrence annulée doit rester visible, pas être filtrée) —
     * {@code status} distingue une occurrence normale d'une annulée,
     * voir {@code ActivityController#toDetailResponse}.
     */
    public record OccurrenceSummary(
            Long id, Long scheduleId, Instant startAt, Instant endAt, Long locationId, String status) {

        public static OccurrenceSummary from(Occurrence occurrence) {
            return new OccurrenceSummary(
                    occurrence.id(), occurrence.scheduleId(), occurrence.startAt(), occurrence.endAt(),
                    occurrence.locationId(), occurrence.status());
        }
    }

    /** Un lieu résolu (schedule par défaut ou occurrence exceptionnelle) — voir {@link Location}. */
    public record LocationSummary(
            Long id,
            String name,
            String address,
            String city,
            String postalCode,
            String region,
            Double latitude,
            Double longitude,
            String timezone,
            String coordinatePrecision) {

        public static LocationSummary from(Location location) {
            return new LocationSummary(
                    location.id(), location.name(), location.address(), location.city(), location.postalCode(),
                    location.region(), location.latitude(), location.longitude(), location.timezone(),
                    location.coordinatePrecision().name());
        }
    }

    /** Un média attaché à l'activité, dans l'ordre d'affichage — voir {@link Media}. */
    public record MediaSummary(
            Long id, String url, String type, Integer width, Integer height, String credit, String altText) {

        public static MediaSummary from(Media media) {
            return new MediaSummary(
                    media.id(), media.url(), media.type(), media.width(), media.height(),
                    media.credit(), media.altText());
        }
    }

    /** Un lien ou contact — voir {@link ActivityLink}. */
    public record LinkSummary(String type, String label, String value) {

        public static LinkSummary from(ActivityLink link) {
            return new LinkSummary(link.type(), link.label(), link.value());
        }
    }

    /** Fiche d'accessibilité — voir {@link Accessibility}. */
    public record AccessibilitySummary(
            Boolean motorImpairment,
            Boolean hearingImpairment,
            Boolean visualImpairment,
            Boolean psychicImpairment,
            Boolean intellectualImpairment) {

        public static AccessibilitySummary from(Accessibility accessibility) {
            return new AccessibilitySummary(
                    accessibility.motorImpairment(), accessibility.hearingImpairment(),
                    accessibility.visualImpairment(), accessibility.psychicImpairment(),
                    accessibility.intellectualImpairment());
        }
    }
}
