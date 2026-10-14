package com.locallife.backend.place.application;

import com.locallife.backend.collector.domain.CollectedPlace;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.DataTourismeCollectorFactory;
import com.locallife.backend.place.application.PlaceImportReport.Verdict;
import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.CoordinatePrecisionPolicy;
import com.locallife.backend.place.domain.PlaceModerationPolicy;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.domain.PlaceTypeWhitelist;
import com.locallife.backend.place.infrastructure.PlaceRepository;
import com.locallife.backend.place.infrastructure.PlaceRepository.PlaceFields;
import com.locallife.backend.place.infrastructure.PlaceRepository.TrackedPlace;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Import des lieux permanents (ADR-0011, phase B) depuis une source {@code DATATOURISME_LIEUX}.
 *
 * <ul>
 *   <li>liste blanche explicite ({@link PlaceTypeWhitelist}) : un lieu hors liste n'est pas importé ;</li>
 *   <li>tout nouveau lieu est créé {@link PlaceStatus#PENDING} : rien n'est publié sans modération ;</li>
 *   <li>lieu connu : données mises à jour, statut selon {@link PlaceModerationPolicy#onReimport} (refus durable,
 *       retour en attente sur changement important) ;</li>
 *   <li>lieu disparu : archivé, sauf disparition massive
 *       ({@link PlaceModerationPolicy#isSuspiciousDisappearance}) ;</li>
 *   <li>collecte en échec : rien n'est écrit ni archivé ;</li>
 *   <li>essai à blanc : même calcul, aucune écriture.</li>
 * </ul>
 * Un import réel s'exécute en une transaction : en cas d'erreur, rien n'est écrit.
 */
@Service
public class PlaceImportService {

    public static final String PLACE_SOURCE_TYPE = "DATATOURISME_LIEUX";

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceImportService.class);
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final SourceService sourceService;
    private final DataTourismeCollectorFactory collectorFactory;
    private final PlaceRepository placeRepository;
    private final TransactionTemplate transactionTemplate;

    public PlaceImportService(
            SourceService sourceService, DataTourismeCollectorFactory collectorFactory,
            PlaceRepository placeRepository, TransactionTemplate transactionTemplate) {
        this.sourceService = sourceService;
        this.collectorFactory = collectorFactory;
        this.placeRepository = placeRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** Import quotidien des sources de lieux actives (4 h 30, heure de Paris, configurable). */
    @Scheduled(cron = "${app.places.import-cron:0 30 4 * * *}", zone = "Europe/Paris")
    public void scheduledImport() {
        for (Source source : sourceService.getAllSources()) {
            if (PLACE_SOURCE_TYPE.equals(source.type()) && ACTIVE_STATUS.equals(source.status())) {
                PlaceImportReport report = importSource(source, false);
                LOGGER.info("Import des lieux '{}' : {}", source.name(), report);
            }
        }
    }

    /** Import réel ou essai à blanc d'une source de lieux ; vide si la source n'existe pas ou n'est pas de ce type. */
    public Optional<PlaceImportReport> run(Long sourceId, boolean dryRun) {
        return sourceService.getSourceById(sourceId)
                .filter(source -> PLACE_SOURCE_TYPE.equals(source.type()))
                .map(source -> importSource(source, dryRun));
    }

    PlaceImportReport importSource(Source source, boolean dryRun) {
        List<CollectedPlace> collected;
        try {
            collected = collectorFactory.createPlaceCollector(source).collect();
        } catch (CollectorException exception) {
            LOGGER.warn("Import des lieux '{}' : collecte en échec : {}", source.name(), exception.getMessage());
            return PlaceImportReport.failed(source.id(), source.name(), dryRun,
                    "Collecte en échec, rien n'est écrit ni archivé : " + exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.warn("Import des lieux '{}' : erreur inattendue", source.name(), exception);
            return PlaceImportReport.failed(source.id(), source.name(), dryRun,
                    "Collecte en échec, rien n'est écrit ni archivé : erreur inattendue ("
                            + exception.getClass().getSimpleName() + "), détail dans les journaux.");
        }
        if (dryRun) {
            return apply(source, collected, true);
        }
        PlaceImportReport report = transactionTemplate.execute(status -> apply(source, collected, false));
        sourceService.recordSync(source.id(), LocalDateTime.now());
        return report;
    }

    private PlaceImportReport apply(Source source, List<CollectedPlace> collected, boolean dryRun) {
        Map<String, TrackedPlace> tracked = placeRepository.findTrackedBySource(source.id());
        Set<String> seen = new HashSet<>();
        List<String> unchanged = new ArrayList<>();
        int whitelisted = 0;
        int created = 0;
        int updated = 0;
        int backToPending = 0;
        int reappeared = 0;
        int rejectedKept = 0;

        for (CollectedPlace place : collected) {
            Optional<String> placeType = PlaceTypeWhitelist.placeType(place.types());
            if (placeType.isPresent()) {
                whitelisted++;
            }
            TrackedPlace known = tracked.get(place.externalId());
            if (known == null) {
                if (placeType.isPresent()) {
                    created++;
                    if (!dryRun) {
                        placeRepository.insert(source.id(), place.externalId(), sourceTypes(place),
                                contentHash(place), fields(place, placeType.get()), PlaceStatus.PENDING);
                    }
                }
                continue;
            }
            seen.add(place.externalId());
            boolean addressLost = hasText(known.address()) && !hasText(place.address());
            double moved = distanceMeters(known.latitude(), known.longitude(), place.latitude(), place.longitude());
            PlaceStatus next = PlaceModerationPolicy.onReimport(known.status(), placeType.isPresent(), moved,
                    addressLost);
            String hash = contentHash(place);
            if (next == known.status() && hash.equals(known.contentHash())) {
                unchanged.add(place.externalId());
                continue;
            }
            if (known.status() == PlaceStatus.REJECTED) {
                rejectedKept++;
            } else if (known.status() == PlaceStatus.PUBLISHED && next == PlaceStatus.PENDING) {
                backToPending++;
            } else if (known.status() == PlaceStatus.ARCHIVED && next == PlaceStatus.PENDING) {
                reappeared++;
            }
            updated++;
            if (!dryRun) {
                // Un lieu sorti de la liste blanche garde son type tant qu'un administrateur ne l'a pas revu.
                String type = placeType.orElse(null);
                placeRepository.update(known.locationId(), source.id(), place.externalId(), sourceTypes(place), hash,
                        fields(place, type == null ? known.placeType() : type), next);
            }
        }
        if (!dryRun) {
            placeRepository.markSeen(source.id(), unchanged);
        }

        List<TrackedPlace> disappeared = tracked.values().stream()
                .filter(place -> !seen.contains(place.externalId()))
                .filter(place -> PlaceModerationPolicy.onDisappearance(place.status()) != place.status())
                .toList();
        long active = tracked.values().stream()
                .filter(place -> place.status() == PlaceStatus.PENDING || place.status() == PlaceStatus.PUBLISHED)
                .count();
        List<String> problems = new ArrayList<>();
        int archived = 0;
        int archiveWithheld = 0;
        if (PlaceModerationPolicy.isSuspiciousDisappearance(disappeared.size(), (int) active)) {
            archiveWithheld = disappeared.size();
            problems.add("Archivage suspendu : " + archiveWithheld + " lieux sur " + active
                    + " ont disparu de la collecte (garde-fou : au moins "
                    + PlaceModerationPolicy.MASS_ARCHIVE_MIN_COUNT + " et plus de 50 %).");
        } else {
            archived = disappeared.size();
            if (!dryRun) {
                disappeared.forEach(place -> placeRepository.updateStatus(place.locationId(), PlaceStatus.ARCHIVED));
            }
        }
        if (whitelisted == 0) {
            problems.add("Aucun lieu retenu par la liste blanche : filtre de départements ou fichier à vérifier.");
        }
        Verdict verdict = problems.isEmpty() ? Verdict.OK : Verdict.SUSPICIOUS;
        return new PlaceImportReport(source.id(), source.name(), dryRun, verdict, collected.size(), whitelisted,
                created, updated, backToPending, reappeared, archived, archiveWithheld, rejectedKept,
                List.copyOf(problems));
    }

    private static PlaceFields fields(CollectedPlace place, String placeType) {
        CoordinatePrecision precision = CoordinatePrecisionPolicy.inferFromText(
                place.name(), place.address(), place.city());
        return new PlaceFields(place.name(), placeType, place.address(), place.postalCode(), place.city(),
                place.latitude(), place.longitude(), place.description(), place.website(), precision);
    }

    private static String sourceTypes(CollectedPlace place) {
        return String.join(",", new TreeSet<>(place.types()));
    }

    /** Empreinte des données de la source, pour ne réécrire que ce qui change. */
    static String contentHash(CollectedPlace place) {
        String content = String.join("\u001f", String.valueOf(place.name()), String.valueOf(place.address()),
                String.valueOf(place.postalCode()), String.valueOf(place.city()), String.valueOf(place.latitude()),
                String.valueOf(place.longitude()), String.valueOf(place.description()),
                String.valueOf(place.website()), sourceTypes(place));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    /** Distance orthodromique ; 0 si une position manque (pas de déplacement établi). */
    static double distanceMeters(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0;
        }
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
