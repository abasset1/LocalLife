package com.locallife.backend.collector.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.accessibility.domain.Accessibility;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedAccessibility;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.metadata.domain.ActivityMetadata;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.RecurrenceRuleService;
import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.tag.domain.ActivityTag;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Pipeline de normalisation (LL-5005, validation renforcée en LL-6002,
 * étendu en LL-11011) : {@code CollectedActivity} → validation →
 * {@code Activity} + le reste du modèle événementiel riche du Sprint 11
 * ({@code Location}/{@code Schedule}/{@code Occurrence}/{@code Media}/
 * {@code ActivityLink}/{@code Accessibility}/{@code ActivityTag}/{@code
 * ActivityMetadata}) — voir {@code docs/05_Sprints/SPRINT_11.md},
 * section 14 (« flux cible »). Volontairement séparé d'{@code
 * ActivityService} (critère d'acceptation LL-5005 : « aucune logique
 * spécifique à un collecteur dans ActivityService ») — ce service ne
 * fait que convertir en mémoire, il ne persiste rien ; la persistance
 * via les services métier existants relève d'{@code ImportService}
 * (LL-5008/LL-11011).
 *
 * <b>Refonte LL-11011</b> : {@link #normalize} restait jusqu'ici la
 * seule méthode de ce service (une {@code CollectedActivity} produisant
 * au plus une {@code Activity}). Le flux cible du ticket exige de
 * dériver bien davantage d'une même donnée collectée ; plutôt que de
 * faire porter à {@link #normalize} la construction de neuf entités à
 * la fois (mélangeant validation du cœur de {@code Activity} avec des
 * données annexes qui ne doivent jamais faire rejeter l'activité — voir
 * {@code SPRINT_11.md}, « aucune perte volontaire des données déjà
 * disponibles »), une méthode {@code normalizeXxx} dédiée est ajoutée
 * par entité cible (voir plus bas) : chacune est un simple convertisseur
 * pur, sans effet sur la validité globale calculée par {@link
 * #normalize}. {@code ImportService} les enchaîne après avoir persisté
 * l'{@code Activity} (pour disposer de son {@code id}).
 *
 * Validation cohérente avec ce que le reste du projet impose déjà à une
 * {@code Activity} (bornes de {@code latitude}/{@code longitude}
 * identiques à celles d'{@code ActivityService}, dupliquées ici plutôt
 * que factorisées — refactoriser {@code ActivityService} n'est pas
 * demandé par ce ticket) :
 *
 * <ul>
 *   <li>{@code title} non vide, longueur ≤ 255 caractères (alignée sur
 *       la colonne {@code activity.title}) ;</li>
 *   <li>{@code location} non nul, avec {@code latitude}/{@code
 *       longitude} renseignées et dans les bornes usuelles (LL-11011 :
 *       remplace la validation historique des champs plats {@code
 *       latitude}/{@code longitude} de {@code CollectedActivity},
 *       supprimés par ce ticket — voir sa javadoc) ;</li>
 *   <li>{@code timings} non vide, avec au moins un horaire dont {@code
 *       begin} est renseigné (LL-11011 : remplace la validation
 *       historique de {@code startDate != null}) ;</li>
 *   <li>la période globale calculée (voir {@link #computeStartDate}/
 *       {@link #computeEndDate}) reste cohérente (fin non antérieure au
 *       début, LL-6002, problème n°3 de {@code DATA_QUALITY_AUDIT.md}) ;</li>
 *   <li>{@code category}, si renseignée (non nulle), non vide/blanche
 *       après {@code trim()} ;</li>
 *   <li>{@code sourceUrl}, si renseignée, doit être une URL absolue
 *       {@code http}/{@code https} syntaxiquement valide (LL-6002) ;</li>
 *   <li>{@code recurrenceRule}, si renseignée, doit être un RRULE RFC
 *       5545 valide (LL-11011, délégué à {@link RecurrenceRuleService},
 *       injecté ici uniquement pour cette validation en mémoire — ce
 *       service ne persiste toujours rien) : une règle invalide rejette
 *       toute l'activité plutôt que d'échouer plus tard, lors de la
 *       création du {@code Schedule} par {@code ImportService}, une
 *       fois l'{@code Activity} déjà persistée.</li>
 * </ul>
 *
 * {@code description} et la borne de fin de la période restent
 * optionnels, comme sur {@code Activity} elle-même.
 */
@Service
public class NormalizationService {

    /** Alignée sur la colonne {@code activity.title} (V2__create_activity_table.sql). */
    private static final int MAX_TITLE_LENGTH = 255;

    /** Longueur de {@code activity_tag.tag} (V24). */
    private static final int MAX_TAG_LENGTH = 100;

    private final RecurrenceRuleService recurrenceRuleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NormalizationService(RecurrenceRuleService recurrenceRuleService) {
        this.recurrenceRuleService = recurrenceRuleService;
    }

    /**
     * Convertit une donnée collectée en {@code Activity} (cœur du modèle,
     * consultable/recherchable comme avant LL-11011), ou la rejette si
     * elle ne respecte pas les règles de validation ci-dessus.
     */
    public Optional<Activity> normalize(CollectedActivity collected) {
        if (!isValid(collected)) {
            return Optional.empty();
        }
        CollectedLocation location = collected.location();
        LocalDateTime startDate = computeStartDate(collected);
        LocalDateTime endDate = computeEndDate(collected);
        Activity activity = new Activity(
                null,
                collected.title(),
                collected.description(),
                collected.category(),
                location.latitude(),
                location.longitude(),
                startDate,
                endDate,
                // ⚠️ Décision à valider (héritée de LL-5005) : PUBLISHED plutôt que PENDING.
                "PUBLISHED",
                // sourceId/importKey laissés à null ici : remplis par ImportService juste
                // avant la sauvegarde, voir sa javadoc.
                null,
                null,
                collected.sourceUrl(),
                location.address(),
                location.city(),
                location.postalCode(),
                collected.longDescription(),
                collected.conditions(),
                collected.ageMin(),
                collected.ageMax(),
                // createdBy (LL-EF-010) : toujours null pour une activité importée —
                // seules les contributions manuelles ont un créateur identifié, voir
                // ActivityService#createActivity et Activity#createdBy.
                // Catégories (issue #45) : portées par activity_category, décidées par
                // ClassificationService et enregistrées par ImportService avec l'activité.
                null);
        return Optional.of(activity);
    }

    /**
     * Localisation structurée (LL-11011, {@code SPRINT_11.md}, section
     * 14 : « localisation structurée »). Même garde-fou qu'{@code
     * LocationService#create} (« aucun lieu ne doit être créé
     * artificiellement à partir de données insuffisantes ») reproduit
     * ici plutôt qu'invoqué directement : ce service ne dépend d'aucun
     * repository, voir sa javadoc de classe.
     *
     * @return {@link Optional#empty()} si {@code collected.location()}
     *     est absente, ou ne porte ni nom, ni adresse, ni coordonnées
     *     complètes.
     */
    public Optional<Location> normalizeLocation(CollectedActivity collected) {
        CollectedLocation source = collected.location();
        if (source == null) {
            return Optional.empty();
        }
        boolean hasName = hasText(source.name());
        boolean hasAddress = hasText(source.address());
        boolean hasCoordinates = source.latitude() != null && source.longitude() != null;
        if (!hasName && !hasAddress && !hasCoordinates) {
            return Optional.empty();
        }
        return Optional.of(new Location(
                null,
                source.name(),
                source.address(),
                source.postalCode(),
                source.city(),
                null,
                source.region(),
                source.countryCode(),
                null,
                source.latitude(),
                source.longitude(),
                source.timezone(),
                null,
                null,
                null));
    }

    /**
     * Programmation de l'activité (LL-11002/LL-11011). Un seul {@code
     * Schedule} par activité importée à ce stade (l'expansion en
     * plusieurs horaires distincts se fait au niveau des {@code
     * Occurrence}, voir {@link #normalizeOccurrences}) : {@code
     * startTime}/{@code endTime} repris du premier horaire collecté
     * (hypothèse d'un horaire de journée homogène d'une occurrence à
     * l'autre, cohérente avec la donnée réellement disponible côté
     * OpenAgenda — voir sa javadoc) ; {@code validFrom}/{@code
     * validUntil} bornent l'ensemble des horaires collectés.
     *
     * @return {@link Optional#empty()} si {@code activityId} est absent
     *     (activité pas encore persistée) ou si aucun horaire exploitable
     *     n'est disponible.
     */
    public Optional<Schedule> normalizeSchedule(CollectedActivity collected, Long activityId, Long locationId) {
        List<CollectedTiming> timings = collected.timings();
        if (activityId == null || timings == null || timings.isEmpty()) {
            return Optional.empty();
        }
        CollectedTiming first = timings.get(0);
        if (first.begin() == null) {
            return Optional.empty();
        }
        LocalDate validFrom = computeStartDate(collected).toLocalDate();
        // recurrenceRule non nul : la borne de fin reste ouverte (voir ADR-0003/RecurrenceRuleService),
        // seule computeEndDate() (bornée aux horaires déjà collectés) serait trompeuse dans ce cas.
        LocalDate validUntil = collected.recurrenceRule() == null ? computeEndDate(collected).toLocalDate() : null;
        String timezone = collected.location() != null ? collected.location().timezone() : null;
        return Optional.of(new Schedule(
                null,
                activityId,
                locationId,
                first.begin().toLocalTime(),
                first.end() != null ? first.end().toLocalTime() : null,
                validFrom,
                validUntil,
                timezone,
                collected.recurrenceRule()));
    }

    /**
     * Occurrences concrètes (LL-11004/LL-11011, « plusieurs horaires
     * supportés ») : une {@code Occurrence} par {@link CollectedTiming}
     * de {@code collected.timings()} — contrairement à une récurrence
     * calculée par {@link RecurrenceRuleService}, ces dates sont déjà
     * connues explicitement (données de la source), donc directement
     * matérialisées plutôt que recalculées.
     *
     * {@code locationId} identique pour toutes les occurrences d'un même
     * import : aucune source actuellement collectée (OpenAgenda) ne
     * fournit de lieu différent par créneau — voir la javadoc
     * d'{@code Occurrence} pour le cas (LL-11005) où cela diffère par la
     * suite (édition manuelle d'une occurrence).
     */
    public List<Occurrence> normalizeOccurrences(CollectedActivity collected, Long scheduleId, Long locationId) {
        if (scheduleId == null || collected.timings() == null) {
            return List.of();
        }
        ZoneId zone = resolveZone(collected.location());
        List<Occurrence> occurrences = new ArrayList<>();
        for (CollectedTiming timing : collected.timings()) {
            if (timing.begin() == null) {
                continue;
            }
            Instant startAt = timing.begin().atZone(zone).toInstant();
            Instant endAt = timing.end() != null ? timing.end().atZone(zone).toInstant() : null;
            occurrences.add(new Occurrence(
                    null, scheduleId, startAt, endAt, locationId, OccurrenceService.STATUS_SCHEDULED, false));
        }
        return occurrences;
    }

    /**
     * Médias (LL-11007/LL-11011, « médias conservés »). {@code type}
     * fixé à {@code "image"} : seul type actuellement produit par
     * {@code OpenAgendaCollector} (voir sa javadoc) — {@code
     * CollectedMedia} ne porte pas lui-même de type, une source future
     * exposant d'autres types de médias devra étendre ce sous-modèle.
     */
    public List<Media> normalizeMedia(CollectedActivity collected) {
        if (collected.media() == null) {
            return List.of();
        }
        return collected.media().stream()
                .filter(item -> hasText(item.url()))
                .map(item -> new Media(null, item.url(), "image", item.width(), item.height(),
                        item.credit(), item.altText()))
                .toList();
    }

    /**
     * Liens/contacts (LL-11008/LL-11011, « liens conservés »). {@code
     * ActivityLinkService#create} (appelé par {@code ImportService}, pas
     * ici) rejette une valeur invalide pour le {@code type} donné —
     * cette méthode ne fait que reformer les {@code ActivityLink} en
     * mémoire, sans validation (cohérent avec le reste de ce service :
     * la validation d'un lien individuel ne doit pas faire rejeter
     * toute l'activité).
     */
    public List<ActivityLink> normalizeLinks(CollectedActivity collected, Long activityId) {
        if (collected.links() == null || activityId == null) {
            return List.of();
        }
        return collected.links().stream()
                .filter(item -> hasText(item.value()))
                .map(item -> new ActivityLink(null, activityId, item.type(), item.label(), item.value()))
                .toList();
    }

    /**
     * Accessibilité (LL-11009/LL-11011). {@link Optional#empty()} si la
     * source n'a rien renseigné du tout (les cinq champs {@code null}) :
     * évite de créer une ligne {@code accessibility} entièrement vide,
     * même principe que {@link #normalizeLocation}.
     */
    public Optional<Accessibility> normalizeAccessibility(CollectedActivity collected, Long activityId) {
        CollectedAccessibility source = collected.accessibility();
        if (source == null || activityId == null) {
            return Optional.empty();
        }
        boolean anyKnown = source.motorImpairment() != null || source.hearingImpairment() != null
                || source.visualImpairment() != null || source.psychicImpairment() != null
                || source.intellectualImpairment() != null;
        if (!anyKnown) {
            return Optional.empty();
        }
        return Optional.of(new Accessibility(
                null, activityId, source.motorImpairment(), source.hearingImpairment(),
                source.visualImpairment(), source.psychicImpairment(), source.intellectualImpairment()));
    }

    /**
     * Tags (LL-11009/LL-11011, « tags conservés »). Valeurs vides/blanches
     * ignorées et doublons supprimés (contrainte {@code UNIQUE(activity_id,
     * tag)} de {@code V24__create_accessibility_tag_metadata_tables.sql} :
     * mieux vaut filtrer ici qu'échouer plus tard à la sauvegarde). Même
     * raison pour la longueur : un tag de plus de {@value #MAX_TAG_LENGTH}
     * caractères (colonne {@code activity_tag.tag}) est tronqué, sinon son
     * insertion échoue et fait perdre tout le reste de l'enrichissement de
     * l'activité — constaté sur un import réel le 09/10/2026.
     */
    public List<ActivityTag> normalizeTags(CollectedActivity collected, Long activityId) {
        if (collected.tags() == null || activityId == null) {
            return List.of();
        }
        return collected.tags().stream()
                .filter(this::hasText)
                .map(String::trim)
                .map(tag -> tag.length() <= MAX_TAG_LENGTH ? tag : tag.substring(0, MAX_TAG_LENGTH).trim())
                .distinct()
                .map(tag -> new ActivityTag(null, activityId, tag))
                .toList();
    }

    /**
     * Informations d'inscription (LL-11011, « informations disponibles
     * relatives à l'inscription ») : pas encore jugées assez importantes
     * pour une colonne dédiée sur {@code Activity} (même raisonnement
     * que {@code ActivityMetadata}, LL-11009), donc portées sous forme
     * JSON minimale ({@code {"registration": "..."}}) plutôt que par une
     * nouvelle table — {@code ActivityMetadataService#create} valide déjà
     * la syntaxe JSON avant persistance.
     */
    public Optional<ActivityMetadata> normalizeMetadata(CollectedActivity collected, Long activityId) {
        if (activityId == null || !hasText(collected.registrationInfo())) {
            return Optional.empty();
        }
        String json = writeJsonObject("registration", collected.registrationInfo());
        return Optional.of(new ActivityMetadata(null, activityId, json));
    }

    private String writeJsonObject(String key, String value) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(key, value));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Ne peut arriver : une Map<String, String> est toujours sérialisable.
            throw new IllegalStateException(exception);
        }
    }

    private ZoneId resolveZone(CollectedLocation location) {
        if (location != null && location.timezone() != null) {
            try {
                return ZoneId.of(location.timezone());
            } catch (DateTimeException exception) {
                return ZoneOffset.UTC;
            }
        }
        return ZoneOffset.UTC;
    }

    private boolean isValid(CollectedActivity collected) {
        return hasValidTitle(collected.title())
                && isValidLocation(collected.location())
                && isValidTimings(collected.timings())
                && isValidDateRange(collected)
                && isValidCategory(collected.category())
                && isValidUrl(collected.sourceUrl())
                && isValidRecurrenceRule(collected.recurrenceRule());
    }

    private boolean hasValidTitle(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TITLE_LENGTH;
    }

    private boolean isValidLocation(CollectedLocation location) {
        return location != null
                && location.latitude() != null && location.latitude() >= -90 && location.latitude() <= 90
                && location.longitude() != null && location.longitude() >= -180 && location.longitude() <= 180;
    }

    private boolean isValidTimings(List<CollectedTiming> timings) {
        return timings != null && !timings.isEmpty() && timings.stream().anyMatch(t -> t.begin() != null);
    }

    private boolean isValidDateRange(CollectedActivity collected) {
        LocalDateTime start = computeStartDate(collected);
        LocalDateTime end = computeEndDate(collected);
        return start == null || end == null || !end.isBefore(start);
    }

    private boolean isValidCategory(String category) {
        return category == null || !category.isBlank();
    }

    private boolean isValidUrl(String url) {
        if (url == null) {
            return true;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean isValidRecurrenceRule(String recurrenceRule) {
        if (recurrenceRule == null) {
            return true;
        }
        try {
            recurrenceRuleService.validate(recurrenceRule);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Date de début « pertinente » pour {@code Activity} (recherche/
     * affichage carte, voir la javadoc de {@code Activity}) : la plus
     * ancienne de {@code collected.timings()} — {@code null} si aucun
     * horaire exploitable (cas déjà exclu par {@link #isValid} au moment
     * où {@link #normalize} l'appelle, mais {@link #normalizeSchedule}
     * y accède aussi indépendamment, d'où cette tolérance).
     */
    private LocalDateTime computeStartDate(CollectedActivity collected) {
        if (collected.timings() == null) {
            return null;
        }
        return collected.timings().stream()
                .map(CollectedTiming::begin)
                .filter(begin -> begin != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * Date de fin « pertinente » : la plus tardive parmi {@code end}
     * (ou, à défaut, {@code begin}) de chaque horaire collecté.
     */
    private LocalDateTime computeEndDate(CollectedActivity collected) {
        if (collected.timings() == null) {
            return null;
        }
        return collected.timings().stream()
                .map(timing -> timing.end() != null ? timing.end() : timing.begin())
                .filter(end -> end != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
