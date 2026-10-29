package com.locallife.backend.collector.application;

import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityCoordinatePrecisionRepository;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ActivityClassificationService;
import com.locallife.backend.activityclassification.application.ClassificationOutcome;
import com.locallife.backend.activityclassification.application.ClassificationService;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectionReport;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.DataTourismeCollectorFactory;
import com.locallife.backend.collector.infrastructure.OpenAgendaCollectorFactory;
import com.locallife.backend.collector.infrastructure.SourceSyncStateRepository;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.place.application.LocationService;
import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.Location;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.ScheduleService;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.source.application.SourceDefaultCategoryService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.tag.application.ActivityTagService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
 * déclenché manuellement), sans redémarrage. Deux types de collecteur
 * existent : OpenAgenda (type {@code API} avec un {@code agendaUid}) et
 * DATAtourisme via data.gouv.fr (type {@code DATATOURISME} avec une
 * {@code url}, voir {@code DataTourismeCollector}) — voir
 * {@link #isCollectible(Source)}.
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
 *       par LL-5009. Seules les activités {@code PUBLISHED} sont archivées,
 *       et un ré-import conserve le statut d'une activité existante (une
 *       annulation par un administrateur n'est jamais écrasée) : seule une
 *       activité archivée par l'import est republiée à son retour.
 *       ⚠️ LL-11011 : les entités enrichies (Location
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
 * <b>Collecte complète, partielle ou en échec (sprint « agendas
 * exhaustifs », AGD-04/05)</b> : le collecteur rend un {@code
 * CollectionReport}. Complète, l'import se déroule comme décrit ci-dessus
 * et {@code lastSyncAt} avance. Partielle (pagination interrompue avant la
 * fin réelle), les éléments reçus sont créés ou mis à jour, mais
 * <b>aucune</b> activité n'est archivée pour absence et {@code lastSyncAt}
 * n'avance pas ; le bilan porte le statut {@code PARTIAL} et la raison.
 * Chaque essai, quel qu'en soit le statut, est consigné dans {@code
 * source_sync_state} (dernier essai distinct du dernier succès complet).
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
 *
 * <b>Import incrémental</b> : un élément déjà importé dont le contenu
 * collecté n'a pas changé depuis son dernier import réussi est sauté
 * (compté dans {@code ImportResult.unchanged}) au lieu d'être réécrit et
 * ré-enrichi — voir {@link ImportFingerprintService}. La collecte, elle,
 * reste complète : toutes les clés vues alimentent toujours l'archivage
 * des activités disparues de la source.
 *
 * <b>Déduplication entre sources</b> : un même événement peut être
 * publié par plusieurs agendas d'un même fournisseur (événement OpenAgenda
 * partagé). Un élément dont une <b>autre</b> source a déjà une activité
 * non archivée (même identifiant externe, même titre, même date de début,
 * voir {@link ImportFingerprintService}) n'est pas importé une seconde
 * fois : il est compté dans {@code ImportResult.duplicates}. La première
 * source qui l'importe en reste propriétaire ; si elle l'archive (élément
 * retiré de son agenda), la source suivante qui le contient encore le
 * (re)publie à son prochain import. Les doublons déjà en base sont
 * résorbés de la même façon : le plus récent des deux est archivé.
 *
 * <b>Déduplication dans une même source</b> : une source peut publier
 * plusieurs fois le même événement sous des identifiants différents (même
 * titre, mêmes horaires, même lieu, voir {@link SameSourceDuplicates}).
 * Un seul est importé : celui qui a déjà l'activité publiée la plus
 * ancienne ici, sinon celui de plus petite clé d'import (choix stable d'un
 * import à l'autre). Les autres sont comptés dans
 * {@code ImportResult.duplicates} et leur activité publiée, s'ils en ont
 * une, est archivée ; une activité dans un autre statut (annulée par un
 * administrateur, par exemple) n'est pas modifiée. Si l'élément conservé
 * disparaît de la source, un doublon restant devient le conservé et son
 * activité est republiée au prochain import.
 *
 * <b>Catégories par défaut de la source</b> : un élément que ni ses
 * mots-clés source ni son titre ne classent reçoit les catégories par
 * défaut de sa source, si un administrateur en a réglé ({@link
 * SourceDefaultCategoryService}) ; sinon il reste en quarantaine, comme
 * avant. Les mots-clés gardent toujours la priorité.
 *
 * <b>Une transaction par élément</b> : toutes les écritures d'un élément collecté (activité, classification,
 * enrichissement, empreinte) partent en une seule transaction, au lieu d'une par requête : un seul commit par
 * élément, ce qui divise la durée d'un import. Un élément en erreur est annulé en entier (compté dans
 * {@code errors}, retenté au prochain import) ; l'enrichissement a son propre point de sauvegarde, son échec
 * laisse l'activité enregistrée, comme avant (LL-11016).
 *
 * <b>Un seul import à la fois</b> : {@link #importAll()} refuse de
 * démarrer ({@link ImportAlreadyRunningException}) si un import est déjà
 * en cours dans cette instance. Verrou en mémoire : suffisant tant que
 * l'application tourne en une seule instance.
 */
@Service
public class ImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportService.class);

    /**
     * Type de source retenu comme collectible via OpenAgenda (LL-EF-005) —
     * voir la Javadoc de la classe.
     */
    private static final String COLLECTIBLE_SOURCE_TYPE = "API";

    /** Type de source collecté via l'export CSV DATAtourisme de data.gouv.fr. */
    private static final String DATATOURISME_SOURCE_TYPE = "DATATOURISME";

    private static final String ACTIVE_STATUS = "ACTIVE";

    /** Statut de suppression douce, voir la javadoc de la classe. */
    private static final String ARCHIVED_STATUS = "ARCHIVED";
    private static final String PUBLISHED_STATUS = "PUBLISHED";

    /**
     * Garde-fou d'archivage : si au moins {@value #MASS_ARCHIVE_MIN_COUNT} activités publiées <b>à venir</b>
     * disparaissent d'un coup, et qu'elles représentent plus de la moitié des activités publiées à venir de
     * la source, la collecte est jugée suspecte (fichier tronqué, filtre mal saisi, panne côté source) :
     * aucune activité à venir n'est archivée pour cette exécution. Les activités passées ne comptent pas et
     * sont archivées dans tous les cas : leur disparition de la source est normale.
     *
     * <p>Limites exactes : suspect si {@code disparues >= 10} <b>et</b> {@code disparues > 0,5 × publiées à
     * venir} (strictement plus de la moitié : 10 sur 20 n'est pas suspect, 10 sur 19 l'est). Valeurs initiales
     * de protection technique, pas des règles métier : à ajuster sur l'observation des imports réels.
     */
    static final int MASS_ARCHIVE_MIN_COUNT = 10;

    static final double MASS_ARCHIVE_MAX_RATIO = 0.5;

    private final SourceService sourceService;
    private final OpenAgendaCollectorFactory openAgendaCollectorFactory;
    private final DataTourismeCollectorFactory dataTourismeCollectorFactory;
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
    private final ActivityClassificationService activityClassificationService;
    private final ImportFingerprintService importFingerprintService;
    private final SourceDefaultCategoryService sourceDefaultCategoryService;
    private final ActivityCoordinatePrecisionRepository activityCoordinatePrecisionRepository;
    private final SourceSyncStateRepository sourceSyncStateRepository;

    /**
     * Transaction d'un élément, et point de sauvegarde de son enrichissement à l'intérieur de celle-ci : voir la
     * javadoc de la classe (« une transaction par élément »).
     */
    private final TransactionTemplate nestedTransaction;

    /** Vrai pendant un import, voir la javadoc de la classe (« un seul import à la fois »). */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ImportService(
            SourceService sourceService,
            OpenAgendaCollectorFactory openAgendaCollectorFactory,
            DataTourismeCollectorFactory dataTourismeCollectorFactory,
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
            ClassificationService classificationService,
            ActivityClassificationService activityClassificationService,
            ImportFingerprintService importFingerprintService,
            SourceDefaultCategoryService sourceDefaultCategoryService,
            ActivityCoordinatePrecisionRepository activityCoordinatePrecisionRepository,
            SourceSyncStateRepository sourceSyncStateRepository,
            PlatformTransactionManager transactionManager) {
        this.sourceService = sourceService;
        this.openAgendaCollectorFactory = openAgendaCollectorFactory;
        this.dataTourismeCollectorFactory = dataTourismeCollectorFactory;
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
        this.activityClassificationService = activityClassificationService;
        this.importFingerprintService = importFingerprintService;
        this.sourceDefaultCategoryService = sourceDefaultCategoryService;
        this.activityCoordinatePrecisionRepository = activityCoordinatePrecisionRepository;
        this.sourceSyncStateRepository = sourceSyncStateRepository;
        this.nestedTransaction = new TransactionTemplate(transactionManager);
        this.nestedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    /**
     * Exécute l'import pour chaque source collectible actuellement en
     * base (LL-EF-005) — voir {@link #isCollectible(Source)}.
     *
     * @throws ImportAlreadyRunningException si un import est déjà en cours
     */
    public List<ImportResult> importAll() {
        if (!running.compareAndSet(false, true)) {
            throw new ImportAlreadyRunningException();
        }
        try {
            return sourceService.getAllSources().stream()
                    .filter(this::isCollectible)
                    .map(this::importFrom)
                    .toList();
        } finally {
            running.set(false);
        }
    }

    /**
     * Une source est collectible via OpenAgenda si elle est de type
     * {@code API}, active, et porte un {@code agendaUid} non vide — les
     * sources {@code RSS}/{@code MANUAL}, inactives, ou {@code API} sans
     * {@code agendaUid} (créées pour un futur type de collecteur non
     * encore implémenté) sont silencieusement ignorées, plutôt que de
     * tenter une collecte vouée à échouer. Une source {@code DATATOURISME}
     * est collectible si elle est active et porte une {@code url}.
     */
    private boolean isCollectible(Source source) {
        if (!ACTIVE_STATUS.equals(source.status())) {
            return false;
        }
        if (DATATOURISME_SOURCE_TYPE.equals(source.type())) {
            return hasText(source.url());
        }
        return COLLECTIBLE_SOURCE_TYPE.equals(source.type()) && hasText(source.agendaUid());
    }

    private Collector collectorFor(Source source) {
        return collectorFor(source, openAgendaCollectorFactory, dataTourismeCollectorFactory);
    }

    /** Collecteur d'une source selon son type ; partagé avec l'essai à blanc ({@code SourceDryRunService}). */
    static Collector collectorFor(
            Source source, OpenAgendaCollectorFactory openAgenda, DataTourismeCollectorFactory dataTourisme) {
        if (DATATOURISME_SOURCE_TYPE.equals(source.type())) {
            return dataTourisme.create(source);
        }
        return openAgenda.create(source);
    }

    /**
     * Cause affichée dans le bilan : seul le message d'une {@link CollectorException}, rédigé par le collecteur,
     * est repris. Une autre exception peut porter l'URL d'appel, donc une clé d'API : son message ne va que
     * dans les journaux.
     */
    static String displayableCause(RuntimeException exception) {
        if (exception instanceof CollectorException) {
            return exception.getMessage();
        }
        return "erreur inattendue (" + exception.getClass().getSimpleName() + "), détail dans les journaux.";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Importe une source, avec le référentiel de classification chargé une seule fois pour tous ses
     * éléments (voir {@code ClassificationService#beginBatch}).
     */
    private ImportResult importFrom(Source source) {
        classificationService.beginBatch();
        try {
            return importFromWithinBatch(source);
        } finally {
            classificationService.endBatch();
        }
    }

    private ImportResult importFromWithinBatch(Source source) {
        LocalDateTime startedAt = LocalDateTime.now();
        Collector collector = collectorFor(source);

        CollectionReport report;
        try {
            report = collector.collectWithReport();
        } catch (RuntimeException exception) {
            LocalDateTime endedAt = LocalDateTime.now();
            // Le message d'une exception autre que CollectorException peut porter l'URL d'appel, donc la clé :
            // il ne va que dans la pile journalisée, pas dans la ligne de résumé.
            LOGGER.error("Échec de la collecte pour la source '{}' : {}", source.name(),
                    displayableCause(exception), exception);
            String cause = displayableCause(exception);
            ImportResult failed = new ImportResult(source.name(), startedAt, endedAt, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0,
                    "Collecte en échec, import interrompu sans rien créer, modifier ni archiver : " + cause,
                    SyncStatus.FAILED, 0, 0, cause);
            recordSyncState(source, failed);
            return failed;
        }
        List<CollectedActivity> collected = report.activities();

        int created = 0;
        int updated = 0;
        int ignored = 0;
        int needsReview = 0;
        int errors = 0;
        int unchanged = 0;
        int duplicates = 0;
        Set<String> seenKeys = new HashSet<>();
        Map<String, String> knownFingerprints = importFingerprintService.findBySourceId(source.id());
        // Catégories par défaut de la source : dernier recours pour un élément qu'aucun mot-clé ne classe.
        List<Long> defaultCategoryIds = sourceDefaultCategoryService.findCategoryIds(source.id());
        // Lu une fois par source : sert à ne pas re-décider un élément en quarantaine inchangé. Les
        // catégories par défaut en font partie : en régler une re-décide toute la quarantaine de la source.
        String referentialVersion = classificationService.referentialVersion() + "|défaut:" + defaultCategoryIds;
        Set<String> sameSourceDuplicateKeys = sameSourceDuplicateKeys(source, collected);

        ImportContext context = new ImportContext(
                source, knownFingerprints, defaultCategoryIds, referentialVersion, sameSourceDuplicateKeys);

        for (CollectedActivity item : collected) {
            try {
                String key = deduplicationService.computeDeduplicationKey(item);
                seenKeys.add(key);
                // Une transaction par élément (voir la javadoc de la classe) : ses écritures partent en un seul
                // commit, et un échec n'en laisse aucune à moitié faite.
                ItemOutcome outcome = nestedTransaction.execute(status -> importItem(context, item, key));
                switch (outcome) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case IGNORED -> ignored++;
                    case NEEDS_REVIEW -> needsReview++;
                    case UNCHANGED -> unchanged++;
                    case DUPLICATE -> duplicates++;
                }
            } catch (RuntimeException exception) {
                errors++;
                LOGGER.warn("Échec du traitement d'un élément collecté pour la source '{}' : {}",
                        source.name(), exception.getMessage(), exception);
            }
        }

        duplicates += archiveDuplicatesOfOtherSources(source.id());
        // AGD-04 : l'absence d'un élément d'une collecte partielle ne prouve pas qu'il a disparu de la source
        // (il est peut-être sur une page non lue). Aucun archivage des éléments absents dans ce cas, ni des
        // passés ni des à venir : seule une collecte complète décide qu'un élément n'existe plus.
        ArchiveOutcome archive = report.isComplete()
                ? archiveActivitiesNoLongerInSource(source, seenKeys)
                : new ArchiveOutcome(0, 0, report.stopReason() + " Aucun archivage : les éléments absents de "
                        + "cette collecte ne sont ni archivés ni modifiés.");
        LocalDateTime endedAt = LocalDateTime.now();
        SyncStatus status = report.isComplete() ? SyncStatus.SUCCESS : SyncStatus.PARTIAL;

        // lastSyncAt = « dernier import complet réussi » (SOURCE_CONTRACT.md) : une collecte partielle ne
        // l'avance pas, son essai est consigné dans source_sync_state (recordSyncState ci-dessous).
        if (status == SyncStatus.SUCCESS) {
            sourceService.recordSync(source.id(), endedAt);
        }

        ImportResult result = new ImportResult(
                source.name(), startedAt, endedAt, collected.size(), created, updated, ignored, errors,
                archive.archived(), needsReview, unchanged, duplicates, archive.withheld(), archive.reason(),
                status, report.pagesRead(), report.received(), report.stopReason());
        recordSyncState(source, result);
        if (status == SyncStatus.SUCCESS) {
            LOGGER.info("Import terminé pour la source '{}' : {}", source.name(), result);
        } else {
            LOGGER.warn("Import PARTIEL pour la source '{}' : {}", source.name(), result);
        }

        return result;
    }

    /** Traite un élément collecté, dans sa propre transaction (voir la boucle de {@link #importFromWithinBatch}). */
    private ItemOutcome importItem(ImportContext context, CollectedActivity item, String key) {
        Source source = context.source();
        // Déduplication dans la source (voir la javadoc de la classe) : avant l'import incrémental,
        // pour qu'une activité publiée inchangée qui doublonne soit elle aussi archivée.
        if (context.sameSourceDuplicateKeys().contains(key)) {
            archiveIfPublished(activityRepository.findBySourceIdAndImportKey(source.id(), key));
            return ItemOutcome.DUPLICATE;
        }

        Optional<Activity> normalized = normalizationService.normalize(item);
        if (normalized.isEmpty()) {
            return ItemOutcome.IGNORED;
        }

        Optional<Activity> existing = activityRepository.findBySourceIdAndImportKey(source.id(), key);

        // Import incrémental : contenu identique à celui du dernier import réussi → rien à
        // réécrire. Une activité archivée n'est jamais sautée : revenue dans la source, elle
        // doit être republiée.
        String fingerprint = importFingerprintService.compute(item);
        if (existing.isPresent()
                && !ARCHIVED_STATUS.equals(existing.get().status())
                && fingerprint.equals(context.knownFingerprints().get(key))) {
            return ItemOutcome.UNCHANGED;
        }

        // Élément déjà en quarantaine, contenu et référentiel inchangés : la décision
        // serait la même, inutile de la recalculer et de réécrire sa trace.
        String quarantineFingerprint =
                importFingerprintService.computeQuarantined(fingerprint, context.referentialVersion());
        if (existing.isEmpty() && quarantineFingerprint.equals(context.knownFingerprints().get(key))) {
            return ItemOutcome.NEEDS_REVIEW;
        }

        // Déduplication entre sources (voir la javadoc de la classe) : ne concerne qu'un
        // élément sans activité publiée ici (nouveau, ou archivé), jamais une activité que
        // cette source possède déjà.
        boolean publishedHere = existing.isPresent() && !ARCHIVED_STATUS.equals(existing.get().status());
        if (!publishedHere && isDuplicateOfAnotherSource(source, item, normalized.get())) {
            return ItemOutcome.DUPLICATE;
        }

        // CAT-008 : classification centralisée (ClassificationService). Une activité déjà
        // importée garde ses catégories (un ré-import n'écrase jamais une classification
        // existante, y compris validée par un humain). Pour un nouvel élément, si la
        // classification n'est pas décidable (NEEDS_REVIEW / UNCLASSIFIED), aucune activité
        // n'est créée : une activité a au moins une catégorie (activity_category, V42) et
        // aucune catégorie n'est inventée. L'élément est consigné pour relecture
        // (classification_record) et compté dans needsReview (distinct d'ignored).
        // Fallback titre : le titre n'est exploité par ClassificationService que si la
        // source ne fournit aucun keyword (catégorie d'origine / tags absents ou vides).
        // Issue #45 : toutes les catégories détectées (1..N) sont enregistrées avec
        // l'activité, dans la même transaction (ActivityClassificationService#createActivity).
        Activity toSave = withSourceAndKey(
                normalized.get(), existing.map(Activity::id).orElse(null), source.id(), key);
        // Un ré-import met à jour le contenu, jamais une décision de modération : une activité
        // annulée par un administrateur (ou dans tout autre statut posé hors import) le reste.
        // Seule une activité archivée par l'import lui-même (absente de la source) est republiée.
        if (existing.isPresent() && !ARCHIVED_STATUS.equals(existing.get().status())) {
            toSave = withStatus(toSave, existing.get().status());
        }
        Activity saved;
        if (existing.isPresent()) {
            saved = activityRepository.save(toSave);
        } else {
            ClassificationOutcome classification =
                    classificationService.decide(item.title(), item.category(), item.tags());
            if (!classification.classified() && !context.defaultCategoryIds().isEmpty()) {
                ClassificationOutcome byDefault =
                        classificationService.sourceDefault(context.defaultCategoryIds());
                if (byDefault.classified()) {
                    classification = byDefault;
                }
            }
            if (!classification.classified()) {
                classificationService.recordQuarantined(
                        source.id(), key, item.title(), item.category(), item.tags(), classification);
                importFingerprintService.record(
                        source.id(), key, quarantineFingerprint, item.externalId());
                return ItemOutcome.NEEDS_REVIEW;
            }
            saved = activityClassificationService.createActivity(toSave, classification.categoryIds());
            classificationService.recordClassified(
                    source.id(), key, saved.id(), item.title(), item.category(), item.tags(), classification);
        }

        // L'empreinte n'est enregistrée qu'après un enrichissement réussi : sinon l'élément
        // est retenté en entier au prochain import.
        if (enrichActivitySafely(source, saved, item, existing.isPresent())) {
            importFingerprintService.record(source.id(), key, fingerprint, item.externalId());
        } else {
            importFingerprintService.forget(source.id(), key);
        }
        // Compté créé ou mis à jour même si l'enrichissement (secondaire) échoue : l'activité elle-même est
        // bien enregistrée (LL-11016, voir enrichActivitySafely).
        return existing.isPresent() ? ItemOutcome.UPDATED : ItemOutcome.CREATED;
    }

    /** Ce que le traitement d'un élément a donné, pour les compteurs du bilan ({@link ImportResult}). */
    private enum ItemOutcome {
        CREATED, UPDATED, IGNORED, NEEDS_REVIEW, UNCHANGED, DUPLICATE
    }

    /** Données lues une fois par source et communes au traitement de tous ses éléments. */
    private record ImportContext(
            Source source,
            Map<String, String> knownFingerprints,
            List<Long> defaultCategoryIds,
            String referentialVersion,
            Set<String> sameSourceDuplicateKeys) {
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
     *
     * @return {@code true} si l'enrichissement a réussi
     */
    private boolean enrichActivitySafely(
            Source source, Activity saved, CollectedActivity item, boolean alreadyImported) {
        try {
            // Point de sauvegarde : un échec SQL ici n'annule que l'enrichissement, pas l'activité enregistrée.
            nestedTransaction.executeWithoutResult(status -> enrichActivity(saved.id(), item, alreadyImported));
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Échec de l'enrichissement (Location/Schedule/Occurrence/...) de l'activité importée "
                            + "'{}' (id {}) pour la source '{}' : {}",
                    saved.title(), saved.id(), source.name(), exception.getMessage(), exception);
            return false;
        }
    }

    private void enrichActivity(Long activityId, CollectedActivity item, boolean alreadyImported) {
        // Une activité tout juste créée n'a encore aucune entité liée : inutile de les rechercher et de
        // les supprimer (sept requêtes par élément au premier import).
        if (alreadyImported) {
            clearEnrichment(activityId);
        }

        Optional<Location> location = normalizationService.normalizeLocation(item).map(locationService::findOrCreate);
        Long locationId = location.map(Location::id).orElse(null);
        // ADR-0011, phase A : l'activité affiche le point de ce lieu, elle en prend la précision.
        activityCoordinatePrecisionRepository.update(activityId,
                location.map(Location::coordinatePrecision).orElse(CoordinatePrecision.UNKNOWN));

        Optional<Schedule> schedule = normalizationService.normalizeSchedule(item, activityId, locationId);
        if (schedule.isPresent()) {
            Schedule savedSchedule = scheduleService.create(schedule.get());
            // Insertions groupées : une activité peut porter plusieurs centaines de créneaux.
            occurrenceService.createAll(
                    normalizationService.normalizeOccurrences(item, savedSchedule.id(), locationId));
        }

        int position = 0;
        for (Media media : normalizationService.normalizeMedia(item)) {
            Media savedMedia = mediaService.findOrCreate(media);
            activityMediaService.attach(new ActivityMedia(null, activityId, savedMedia.id(), position));
            position++;
        }

        // Un lien invalide (URL/email/téléphone mal saisi côté source) est ignoré seul : il ne
        // doit pas faire échouer le reste de l'enrichissement (accessibilité, tags, métadonnées).
        for (var link : normalizationService.normalizeLinks(item, activityId)) {
            try {
                activityLinkService.create(link);
            } catch (IllegalArgumentException exception) {
                LOGGER.debug("Lien ignoré pour l'activité {} : {}", activityId, exception.getMessage());
            }
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

    /**
     * Vrai si l'élément n'a pas (ou plus) d'activité publiée pour cette source et qu'une autre source en
     * a déjà une pour le même élément. Sans identifiant externe (clé composite), aucun rapprochement
     * n'est tenté.
     */
    private boolean isDuplicateOfAnotherSource(Source source, CollectedActivity item, Activity normalized) {
        if (item.externalId() == null || item.externalId().isBlank()) {
            return false;
        }
        return importFingerprintService.existsLiveDuplicateInAnotherSource(
                source.id(), item.externalId(), normalized.title(), normalized.startDate());
    }

    /**
     * Clés des éléments collectés à ne pas importer parce qu'ils doublonnent un autre élément de la même
     * collecte. Dans chaque groupe, l'élément conservé est celui dont l'activité publiée ici est la plus
     * ancienne (plus petit id), sinon celui de plus petite clé : ainsi l'activité déjà visible le reste.
     */
    private Set<String> sameSourceDuplicateKeys(Source source, List<CollectedActivity> collected) {
        Set<String> duplicateKeys = new HashSet<>();
        // Un élément dont la clé ne se calcule pas est écarté du regroupement ; la boucle d'import le
        // compte en erreur comme avant.
        List<List<String>> groups = SameSourceDuplicates.groups(collected, item -> {
            try {
                return deduplicationService.computeDeduplicationKey(item);
            } catch (RuntimeException exception) {
                return null;
            }
        });
        for (List<String> keys : groups) {
            String kept = keys.stream()
                    .min(Comparator.comparing((String key) -> publishedActivityId(source, key))
                            .thenComparing(Comparator.naturalOrder()))
                    .orElseThrow();
            keys.stream().filter(key -> !key.equals(kept)).forEach(duplicateKeys::add);
        }
        return duplicateKeys;
    }

    /** Id de l'activité publiée de cette clé, {@link Long#MAX_VALUE} si elle n'en a pas (tri du conservé). */
    private long publishedActivityId(Source source, String key) {
        return activityRepository.findBySourceIdAndImportKey(source.id(), key)
                .filter(activity -> PUBLISHED_STATUS.equals(activity.status()))
                .map(Activity::id)
                .orElse(Long.MAX_VALUE);
    }

    /** Archive l'activité d'un doublon si elle est publiée ; tout autre statut (modération) est conservé. */
    private void archiveIfPublished(Optional<Activity> activity) {
        if (activity.isPresent() && PUBLISHED_STATUS.equals(activity.get().status())) {
            activityRepository.save(withStatus(activity.get(), ARCHIVED_STATUS));
        }
    }

    /**
     * Consigne le dernier essai (AGD-05). Un échec d'écriture ici ne doit pas faire perdre le bilan de l'import
     * déjà effectué : journalisé seulement.
     */
    private void recordSyncState(Source source, ImportResult result) {
        try {
            sourceSyncStateRepository.record(source.id(), result.endedAt(), result.status(),
                    result.status() == SyncStatus.SUCCESS ? result.stopReason() : result.blockedReason(),
                    result.pagesRead(), result.received(), result.durationMs());
        } catch (RuntimeException exception) {
            LOGGER.warn("État de synchronisation non enregistré pour la source '{}'", source.name(), exception);
        }
    }

    /**
     * Archive les activités de cette source qui doublonnent une activité plus ancienne d'une autre source
     * (doublons antérieurs à la déduplication). Elles restent dans {@code seenKeys} : l'archivage des
     * éléments disparus ne les concerne pas, et au prochain import elles sont reconnues comme doublons.
     */
    private int archiveDuplicatesOfOtherSources(Long sourceId) {
        int archivedDuplicates = 0;
        for (Long activityId : importFingerprintService.findDuplicateActivityIds(sourceId)) {
            Optional<Activity> duplicate = activityRepository.findById(activityId);
            // Une activité annulée par un administrateur garde ce statut (voir archiveActivitiesNoLongerInSource).
            if (duplicate.isPresent() && PUBLISHED_STATUS.equals(duplicate.get().status())) {
                activityRepository.save(withStatus(duplicate.get(), ARCHIVED_STATUS));
                archivedDuplicates++;
            }
        }
        return archivedDuplicates;
    }

    private ArchiveOutcome archiveActivitiesNoLongerInSource(Source source, Set<String> seenImportKeys) {
        ArchivePlan plan = planArchive(
                activityRepository.findBySourceId(source.id()), seenImportKeys, LocalDate.now().atStartOfDay());
        List<Activity> toArchive = new ArrayList<>(plan.past());
        int withheld = 0;
        String reason = null;
        if (plan.suspicious()) {
            withheld = plan.upcoming().size();
            reason = "Archivage suspendu : " + withheld + " activités à venir sur " + plan.upcomingPublished()
                    + " ont disparu de la collecte (garde-fou : au moins " + MASS_ARCHIVE_MIN_COUNT
                    + " et plus de " + Math.round(MASS_ARCHIVE_MAX_RATIO * 100) + " %). Elles restent publiées.";
            LOGGER.warn("Archivage suspendu pour la source '{}' : {} activités à venir sur {} ont disparu de la "
                    + "collecte, disparition jugée suspecte. Elles restent publiées.",
                    source.name(), withheld, plan.upcomingPublished());
        } else {
            toArchive.addAll(plan.upcoming());
        }
        for (Activity activity : toArchive) {
            activityRepository.save(withStatus(activity, ARCHIVED_STATUS));
        }
        return new ArchiveOutcome(toArchive.size(), withheld, reason);
    }

    /**
     * Activités publiées d'une source absentes de la collecte, séparées en passées et à venir, sans rien
     * écrire. Seules les activités publiées sont concernées : une activité annulée par un administrateur garde
     * ce statut, sinon son retour dans la source la republierait. Partagé avec l'essai à blanc
     * ({@code SourceDryRunService}), pour que l'essai applique exactement la règle de l'import.
     */
    static ArchivePlan planArchive(List<Activity> activities, Set<String> seenImportKeys, LocalDateTime startOfToday) {
        List<Activity> past = new ArrayList<>();
        List<Activity> upcoming = new ArrayList<>();
        int upcomingPublished = 0;
        for (Activity activity : activities) {
            if (!PUBLISHED_STATUS.equals(activity.status())) {
                continue;
            }
            boolean isUpcoming = isUpcoming(activity, startOfToday);
            if (isUpcoming) {
                upcomingPublished++;
            }
            boolean stillPresent = activity.importKey() != null && seenImportKeys.contains(activity.importKey());
            if (!stillPresent) {
                (isUpcoming ? upcoming : past).add(activity);
            }
        }
        return new ArchivePlan(past, upcoming, upcomingPublished);
    }

    /** Résultat de {@link #planArchive} : activités à archiver, et volume de référence du garde-fou. */
    record ArchivePlan(List<Activity> past, List<Activity> upcoming, int upcomingPublished) {

        boolean suspicious() {
            return isSuspiciousArchive(upcoming.size(), upcomingPublished);
        }
    }

    /** Voir {@link #MASS_ARCHIVE_MIN_COUNT}. */
    static boolean isSuspiciousArchive(int upcomingCandidates, int upcomingPublished) {
        return upcomingCandidates >= MASS_ARCHIVE_MIN_COUNT
                && upcomingCandidates > upcomingPublished * MASS_ARCHIVE_MAX_RATIO;
    }

    /** À venir : fin (ou, à défaut, début) aujourd'hui ou plus tard ; sans date, comptée comme à venir. */
    private static boolean isUpcoming(Activity activity, LocalDateTime startOfToday) {
        LocalDateTime last = activity.endDate() != null ? activity.endDate() : activity.startDate();
        return last == null || !last.isBefore(startOfToday);
    }

    /** Bilan de l'archivage : archivées, ou retenues par le garde-fou (avec le motif). */
    private record ArchiveOutcome(int archived, int withheld, String reason) {
    }

    private Activity withSourceAndKey(Activity activity, Long id, Long sourceId, String importKey) {
        return new Activity(
                id, activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                activity.status(), sourceId, importKey, activity.url(),
                activity.address(), activity.city(), activity.postalCode(),
                activity.longDescription(), activity.conditions(), activity.ageMin(), activity.ageMax(),
                activity.createdBy());
    }

    private Activity withStatus(Activity activity, String status) {
        return new Activity(
                activity.id(), activity.title(), activity.description(), activity.category(),
                activity.latitude(), activity.longitude(), activity.startDate(), activity.endDate(),
                status, activity.sourceId(), activity.importKey(), activity.url(),
                activity.address(), activity.city(), activity.postalCode(),
                activity.longDescription(), activity.conditions(), activity.ageMin(), activity.ageMax(),
                activity.createdBy());
    }

}
