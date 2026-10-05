package com.locallife.backend.collector.application;

import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ClassificationOutcome;
import com.locallife.backend.activityclassification.application.ClassificationService;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.OpenAgendaCollectorFactory;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.place.application.LocationService;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.ScheduleService;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.tag.application.ActivityTagService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persistance des imports (LL-5008) : orchestre {@code Collector} →
 * {@code DeduplicationService} → {@code NormalizationService} →
 * {@code ActivityRepository}, « via les services métier existants »
 * (objectif du ticket) — {@code ActivityRepository} et
 * {@code SourceService} plutôt qu'un accès direct aux tables, conformément
 * à la règle du sprint « un collecteur ne doit jamais écrire directement
 * en base » (le collecteur lui-même, {@code Collector}, ne fait toujours
 * que lire la source externe : c'est ce service, pas lui, qui écrit).
 *
 * <b>Sources dynamiques (LL-EF-005)</b> : {@link #importAll()} n'itère
 * plus une {@code List<Collector>} fixée au démarrage de l'application
 * (ancien {@code OpenAgendaSourcesConfig}, supprimé par ce ticket), mais
 * relit {@link SourceService#getAllSources()} à chaque exécution et
 * construit un collecteur ({@link OpenAgendaCollectorFactory}) pour
 * chaque source {@link #isCollectible(Source) collectible} trouvée.
 * Ajouter, modifier ou supprimer un agenda depuis l'interface
 * d'administration prend donc effet dès le prochain import (planifié ou
 * déclenché manuellement), sans redémarrage. Un seul type de collecteur
 * existe à ce jour (OpenAgenda) : {@link #isCollectible(Source)} retient
 * toute source de type {@code API}, active, avec un {@code agendaUid}
 * renseigné — voir sa Javadoc si un second type de collecteur devait être
 * ajouté un jour (ex. RSS).
 *
 * Critères LL-5008 :
 * <ul>
 *   <li><b>aucune duplication</b> : une {@code CollectedActivity} déjà
 *       importée (même {@code source}/{@code importKey}, voir
 *       {@code DeduplicationService}) met à jour l'{@code Activity}
 *       existante ({@code ActivityRepository#findBySourceIdAndImportKey})
 *       plutôt que d'en créer une nouvelle ;</li>
 *   <li><b>activité existante mise à jour si elle appartient à la même
 *       source</b> : la recherche de correspondance est scopée à
 *       {@code source.id()}, jamais globale ;</li>
 *   <li><b>activité supprimée de la source</b> : ⚠️ décision à valider —
 *       stratégie choisie : suppression douce (statut {@code ARCHIVED}),
 *       pas de suppression physique. Une activité déjà présente en base
 *       pour cette source mais absente de la dernière collecte est
 *       archivée plutôt que supprimée : plus prudent pour un MVP (une
 *       panne réseau partielle du collecteur, par exemple, ne doit pas
 *       effacer des activités réelles) et conserve un historique exploitable
 *       par LL-5009. ⚠️ Point à surveiller : la recherche/carte (LL-1007/
 *       LL-4002) ne filtre pas {@code status} par défaut — une activité
 *       {@code ARCHIVED} continuera donc d'apparaître tant qu'un filtre
 *       explicite n'est pas ajouté côté requête/frontend (hors périmètre
 *       de ce ticket). ⚠️ LL-11011 : les entités enrichies (Location
 *       partagée exceptée, voir {@link #enrichActivity}) d'une activité
 *       archivée ne sont ni recalculées ni supprimées à l'archivage — elles
 *       restent telles que laissées par le dernier import réussi, cohérent
 *       avec « conserve un historique exploitable » ci-dessus ;</li>
 *   <li><b>création manuelle non affectée</b> : le balayage d'archivage
 *       est scopé à {@code source.id()} de la source en cours d'import —
 *       les activités manuelles (source {@code MANUAL}) ont un
 *       {@code sourceId} différent et ne sont donc jamais concernées, par
 *       construction (voir {@code ActivityService#createActivity}).</li>
 * </ul>
 *
 * ⚠️ Autre décision à valider : une {@code CollectedActivity} rejetée par
 * {@code NormalizationService} (donnée invalide) n'est pas ajoutée aux
 * clés « vues » de cet import. Si elle correspond à une activité déjà
 * importée précédemment, cette activité sera donc archivée à ce passage
 * (traitée comme absente), plutôt que laissée telle quelle. Comportement
 * jugé acceptable pour un MVP (une donnée redevenue invalide ne doit pas
 * rester affichée telle quelle) mais à surveiller.
 *
 * Aucun déclencheur (endpoint, tâche planifiée) n'est ajouté par ce
 * ticket : ni {@code SPRINT_5.md} ni les critères de LL-5008 n'en
 * demandent un — {@code importAll()} est appelable directement (tests,
 * LL-5010) mais rien n'invoque encore cette méthode dans l'application en
 * cours d'exécution.
 *
 * <b>{@code lastSyncAt} (bug corrigé)</b> : {@link #importFrom(Source)}
 * reporte désormais la date/heure de fin d'un import réussi sur
 * {@code Source.lastSyncAt} (via {@code SourceService#recordSync}),
 * conformément au contrat ({@code SOURCE_CONTRACT.md}) et à la javadoc
 * de {@code SourceService#updateSource}, qui décrivaient déjà ce champ
 * comme renseigné par ce service — {@code endedAt} était calculé pour
 * chaque import (voir {@code ImportResult}) mais n'était jusqu'ici
 * jamais reporté sur la source elle-même, laissant la colonne
 * {@code last_sync_at} et l'affichage « Dernière synchronisation » de
 * l'administration ({@code AdminPage.tsx}) toujours vides. Non reporté
 * en cas d'échec total de {@code collector.collect()} (branche
 * {@code catch} ci-dessous) : cohérent avec « dernier import
 * <i>réussi</i> » du contrat.
 *
 * <b>Journalisation (LL-5009)</b> : chaque import de source produit une
 * ligne de log (niveau {@code INFO}) résumant le résultat — voir
 * {@code ImportResult} pour le détail des compteurs. Pas de tableau de
 * bord d'administration (exclu explicitement par {@code SPRINT_5.md}) :
 * uniquement des logs applicatifs standard (SLF4J), consultables comme
 * n'importe quel autre log de l'application.
 *
 * ⚠️ Décision à valider : le traitement de chaque élément collecté est
 * isolé par un {@code try/catch} — une exception inattendue sur un
 * élément (bug, donnée totalement malformée) est comptée dans
 * {@code errors} et journalisée ({@code WARN}), sans interrompre le
 * traitement des autres éléments de cette source. De même, un échec total
 * de {@code Collector#collect()} (ex. {@code CollectorException} :
 * configuration manquante, panne réseau) est capturé, journalisé
 * ({@code ERROR}) et traduit en un {@code ImportResult} dégradé
 * ({@code fetched=0}, {@code errors=1}) plutôt que de faire échouer
 * {@code importAll()} pour les autres sources.
 *
 * <b>Refonte LL-11011 (« adapter le pipeline d'import »)</b> : jusqu'ici
 * seule l'{@code Activity} (cœur du modèle) était persistée par élément
 * collecté. {@link #importFrom(Source)} persiste désormais, pour chaque
 * élément accepté par {@code NormalizationService}, l'ensemble du modèle
 * événementiel riche du Sprint 11 — voir {@link #enrichActivity} pour le
 * détail. {@code Location}/{@code Media} exceptés (entités explicitement
 * partageables, voir leurs javadoc, réutilisées via {@code findOrCreate}
 * plutôt que dupliquées), toutes les entités liées à une activité sont
 * <b>remplacées</b> (supprimées puis reconstruites) à chaque réimport
 * plutôt que fusionnées avec les données déjà en base — plus simple et
 * plus sûr qu'une comparaison champ à champ (une occurrence disparue de
 * la source, par exemple, disparaît alors elle aussi de la base, ce qui
 * ne serait pas le cas avec un simple « upsert » par id). ⚠️ Décision à
 * valider, comme le reste de la stratégie de mise à jour ci-dessus.
 */
@Service
public class ImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportService.class);

    /**
     * Type de source retenu comme collectible via OpenAgenda (LL-EF-005) —
     * voir la Javadoc de la classe.
     */
    private static final String COLLECTIBLE_SOURCE_TYPE = "API";

    private static final String ACTIVE_STATUS = "ACTIVE";

    /** Statut de suppression douce, voir la javadoc de la classe. */
    private static final String ARCHIVED_STATUS = "ARCHIVED";

    private final SourceService sourceService;
    private final OpenAgendaCollectorFactory openAgendaCollectorFactory;
    private final NormalizationService normalizationService;
    private final DeduplicationService deduplicationService;
    private final ActivityRepository activityRepository;
    private final LocationService locationService;
    private final ScheduleService scheduleService;
    private final OccurrenceService occurrenceService;
    private final MediaService mediaService;
    private final ActivityMediaService activityMediaService;
    private final ActivityLinkService activityLinkService;
    private final AccessibilityService accessibilityService;
    private final ActivityTagService activityTagService;
    private final ActivityMetadataService activityMetadataService;
    private final ClassificationService classificationService;

    public ImportService(
            SourceService sourceService,
            OpenAgendaCollectorFactory openAgendaCollectorFactory,
            NormalizationService normalizationService,
            DeduplicationService deduplicationService,
            ActivityRepository activityRepository,
            LocationService locationService,
            ScheduleService scheduleService,
            OccurrenceService occurrenceService,
            MediaService mediaService,
            ActivityMediaService activityMediaService,
            ActivityLinkService activityLinkService,
            AccessibilityService accessibilityService,
            ActivityTagService activityTagService,
            ActivityMetadataService activityMetadataService,
            ClassificationService classificationService) {
        this.sourceService = sourceService;
        this.openAgendaCollectorFactory = openAgendaCollectorFactory;
        this.normalizationService = normalizationService;
        this.deduplicationService = deduplicationService;
        this.activityRepository = activityRepository;
        this.locationService = locationService;
        this.scheduleService = scheduleService;
        this.occurrenceService = occurrenceService;
        this.mediaService = mediaService;
        this.activityMediaService = activityMediaService;
        this.activityLinkService = activityLinkService;
        this.accessibilityService = accessibilityService;
        this.activityTagService = activityTagService;
        this.activityMetadataService = activityMetadataService;
        this.classificationService = classificationService;
    }

    /**
     * Exécute l'import pour chaque source collectible actuellement en
     * base (LL-EF-005) — voir {@link #isCollectible(Source)}.
     */
    public List<ImportResult> importAll() {
        return sourceService.getAllSources().stream()
                .filter(this::isCollectible)
                .map(this::importFrom)
                .toList();
    }

    /**
     * Une source est collectible via OpenAgenda si elle est de type
     * {@code API}, active, et porte un {@code agendaUid} non vide — les
     * sources {@code RSS}/{@code MANUAL}, inactives, ou {@code API} sans
     * {@code agendaUid} (créées pour un futur type de collecteur non
     * encore implémenté) sont silencieusement ignorées, plutôt que de
     * tenter une collecte vouée à échouer.
     */
    private boolean isCollectible(Source source) {
        return COLLECTIBLE_SOURCE_TYPE.equals(source.type())
                && ACTIVE_STATUS.equals(source.status())
                && source.agendaUid() != null
                && !source.agendaUid().isBlank();
    }

    private ImportResult importFrom(Source source) {
        LocalDateTime startedAt = LocalDateTime.now();
        Collector collector = openAgendaCollectorFactory.create(source);

        List<CollectedActivity> collected;
        try {
            collected = collector.collect();
        } catch (RuntimeException exception) {
            LocalDateTime endedAt = LocalDateTime.now();
            LOGGER.error("Échec de la collecte pour la source '{}' : {}", source.name(), exception.getMessage(),
                    exception);
            return new ImportResult(source.name(), startedAt, endedAt, 0, 0, 0, 0, 1, 0, 0);
        }

        int created = 0;
        int updated = 0;
        int ignored = 0;
        int needsReview = 0;
        int errors = 0;
        Set<String> seenKeys = new HashSet<>();

        for (CollectedActivity item : collected) {
            try {
                String key = deduplicationService.computeDeduplicationKey(item);
                seenKeys.add(key);

                Optional<Activity> normalized = normalizationService.normalize(item);
                if (normalized.isEmpty()) {
                    ignored++;
                    continue;
                }

                Optional<Activity> existing = activityRepository.findBySourceIdAndImportKey(source.id(), key);

                // CAT-008 : classification centralisée (ClassificationService). Une activité déjà
                // importée garde sa catégorie principale (un ré-import n'écrase jamais une
                // classification existante, y compris validée par un humain). Pour un nouvel
                // élément, si la classification n'est pas décidable (NEEDS_REVIEW / UNCLASSIFIED),
                // aucune activité n'est créée : activity.primary_category_id est NOT NULL (V37) et
                // aucune catégorie n'est inventée. L'élément est consigné pour relecture
                // (classification_record) et compté dans needsReview (distinct d'ignored).
                Long primaryCategoryId;
                ClassificationOutcome classification = null;
                if (existing.isPresent()) {
                    primaryCategoryId = existing.get().primaryCategoryId();
                } else {
                    classification = classificationService.decide(item.category(), item.tags());
                    if (!classification.classified()) {
                        classificationService.recordQuarantined(
                                source.id(), key, item.title(), item.category(), item.tags(), classification);
                        needsReview++;
                        continue;
                    }
                    primaryCategoryId = classification.primaryCategoryId();
                }

                Activity toSave = withSourceKeyAndCategory(
                        normalized.get(), existing.map(Activity::id).orElse(null), source.id(), key,
                        primaryCategoryId);
                Activity saved = activityRepository.save(toSave);
                if (classification != null) {
                    classificationService.recordClassified(
                            source.id(), key, saved.id(), item.title(), item.category(), item.tags(), classification);
                }

                // Bug corrigé (LL-11016, révélé par mvn verify en CI) : created/updated
                // s'incrémentaient après enrichActivity(...) — une exception dans
                // l'enrichissement (Location/Schedule/Occurrence/Media/...) faisait donc
                // compter l'élément entier en erreur, alors que l'Activity elle-même venait
                // d'être sauvegardée avec succès juste au-dessus. Compté ici, avant
                // l'enrichissement : reflète fidèlement que l'import de l'activité a réussi,
                // même si son enrichissement (secondaire) échoue ensuite — voir
                // enrichActivitySafely ci-dessous pour l'isolation de cette étape.
                if (existing.isPresent()) {
                    updated++;
                } else {
                    created++;
                }

                enrichActivitySafely(source, saved, item);
            } catch (RuntimeException exception) {
                errors++;
                LOGGER.warn("Échec du traitement d'un élément collecté pour la source '{}' : {}",
                        source.name(), exception.getMessage(), exception);
            }
        }

        int archived = archiveActivitiesNoLongerInSource(source.id(), seenKeys);
        LocalDateTime endedAt = LocalDateTime.now();

        // Bug corrigé : lastSyncAt restait toujours null (jamais reporté sur la Source),
        // alors que SOURCE_CONTRACT.md le documente comme renseigné par ce service. Reporté
        // uniquement ici, dans le chemin où collector.collect() a réussi (pas dans le catch
        // ci-dessus) : conforme au contrat, "date/heure du dernier import réussi".
        sourceService.recordSync(source.id(), endedAt);

        ImportResult result = new ImportResult(
                source.name(), startedAt, endedAt, collected.size(), created, updated, ignored, errors, archived,
                needsReview);
        LOGGER.info("Import terminé pour la source '{}' : {}", source.name(), result);

        return result;
    }

    /**
     * Persiste le reste du modèle événementiel riche pour l'activité
     * {@code activityId} (LL-11011), une fois l'{@code Activity}
     * elle-même sauvegardée. Voir la javadoc de la classe pour la
     * stratégie de remplacement retenue ({@link #clearEnrichment}
     * d'abord, reconstruction ensuite).
     *
     * L'échec d'une étape individuelle (ex. {@code recurrenceRule}
     * rejetée par {@code ScheduleService}, lien mal formé) se propage
     * comme une {@code RuntimeException} normale : capturé par le
     * {@code try/catch} au niveau de l'appelant ({@link
     * #importFrom(Source)}), qui compte l'élément entier en erreur —
     * cohérent avec « aucune logique spécifique à un collecteur dans
     * ActivityService » et la gestion d'erreur déjà en place pour
     * {@code normalize()}, voir la javadoc de la classe.
     */
    /**
     * Isole l'enrichissement (LL-11016) : une exception ici ne doit pas
     * remettre en cause l'import de l'{@code Activity} elle-même, déjà
     * comptée (voir {@code created}/{@code updated} dans {@link
     * #importFrom}) — seule l'enrichissement (secondaire) échoue,
     * journalisé en avertissement, sans incrémenter {@code errors} (qui
     * resterait sinon trompeur : l'élément collecté a bien été importé,
     * seule sa Location/Schedule/Occurrence/Media/... est éventuellement
     * incomplète). L'activité reste alors "nue" (sans ses entités liées)
     * jusqu'au prochain import réussi de ce même élément, qui retentera
     * l'enrichissement en entier (voir {@code clearEnrichment}/{@code
     * enrichActivity}, aucun état partiel à nettoyer entre-temps).
     */
    private void enrichActivitySafely(Source source, Activity saved, CollectedActivity item) {
        try {
            enrichActivity(saved.id(), item);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Échec de l'enrichissement (Location/Schedule/Occurrence/...) de l'activité importée "
                            + "'{}' (id {}) pour la source '{}' : {}",
                    saved.title(), saved.id(), source.name(), exception.getMessage(), exception);
        }
    }

    private void enrichActivity(Long activityId, CollectedActivity item) {
        clearEnrichment(activityId);

        Long locationId = normalizationService.normalizeLocation(item)
                .map(locationService::findOrCreate)
                .map(Location::id)
                .orElse(null);

        Optional<Schedule> schedule = normalizationService.normalizeSchedule(item, activityId, locationId);
        if (schedule.isPresent()) {
            Schedule savedSchedule = scheduleService.create(schedule.get());
            for (var occurrence : normalizationService.normalizeOccurrences(item, savedSchedule.id(), locationId)) {
                occurrenceService.create(occurrence);
            }
        }

        int position = 0;
        for (Media media : normalizationService.normalizeMedia(item)) {
            Media savedMedia = mediaService.findOrCreate(media);
            activityMediaService.attach(new ActivityMedia(null, activityId, savedMedia.id(), position));
            position++;
        }

        for (var link : normalizationService.normalizeLinks(item, activityId)) {
            activityLinkService.create(link);
        }

        normalizationService.normalizeAccessibility(item, activityId).ifPresent(accessibilityService::create);

        for (var tag : normalizationService.normalizeTags(item, activityId)) {
            activityTagService.create(tag);
        }

        normalizationService.normalizeMetadata(item, activityId).ifPresent(activityMetadataService::create);
    }

    /**
     * Supprime les entités enrichies déjà en base pour une activité
     * avant de les reconstruire (LL-11011) — voir la javadoc de la
     * classe. {@code Location}/{@code Media} volontairement exclues
     * (partageables, jamais supprimées ici : voir {@link
     * #enrichActivity}). Les occurrences d'un schedule sont supprimées
     * avant le schedule lui-même (contrainte {@code FOREIGN KEY}, voir
     * la javadoc de {@code ScheduleRepository#deleteByActivityId}).
     */
    private void clearEnrichment(Long activityId) {
        for (Schedule schedule : scheduleService.findByActivityId(activityId)) {
            occurrenceService.deleteByScheduleId(schedule.id());
        }
        scheduleService.deleteByActivityId(activityId);
        activityMediaService.deleteByActivityId(activityId);
        activityLinkService.deleteByActivityId(activityId);
        accessibilityService.deleteByActivityId(activityId);
        activityTagService.deleteByActivityId(activityId);
        activityMetadataService.deleteByActivityId(activityId);
    }

    private int archiveActivitiesNoLongerInSource(Long sourceId, Set<String> seenImportKeys) {
        int archived = 0;
        for (Activity activity : activityRepository.findBySourceId(sourceId)) {
            boolean stillPresent = activity.importKey() != null && seenImportKeys.contains(activity.importKey());
            boolean alreadyArchived = ARCHIVED_STATUS.equals(activity.status());
            if (!stillPresent && !alreadyArchived) {
                activityRepository.save(withStatus(activity, ARCHIVED_STATUS));
                archived++;
            }
        }
        return archived;
    }

    private Activity withSourceKeyAndCategory(
            Activity activity, Long id, Long sourceId, String importKey, Long primaryCategoryId) {
        return new Activity(
                id, activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                activity.status(), sourceId, importKey, activity.url(),
                activity.address(), activity.city(), activity.postalCode(),
                activity.longDescription(), activity.conditions(), activity.ageMin(), activity.ageMax(),
                activity.createdBy(),
                primaryCategoryId);
    }

    private Activity withStatus(Activity activity, String status) {
        return new Activity(
                activity.id(), activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                status, activity.sourceId(), activity.importKey(), activity.url(),
                activity.address(), activity.city(), activity.postalCode(),
                activity.longDescription(), activity.conditions(), activity.ageMin(), activity.ageMax(),
                activity.createdBy(),
                activity.primaryCategoryId());
    }

}
