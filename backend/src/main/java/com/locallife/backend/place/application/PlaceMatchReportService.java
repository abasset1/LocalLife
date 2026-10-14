package com.locallife.backend.place.application;

import com.locallife.backend.place.domain.PlaceMatcher;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
import com.locallife.backend.place.infrastructure.PlaceMatchRepository;
import com.locallife.backend.place.infrastructure.PlaceMatchRepository.CandidatePair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Rapport de rapprochement lieux d'événement ↔ lieux permanents (ADR-0011, § 2.3) — MESURE SANS ÉCRITURE. Aucun
 * lieu n'est modifié, fusionné ni relié.
 *
 * <p>Classement par {@link PlaceMatcher} ; un lieu d'événement qui correspond (certain ou probable) à plusieurs
 * lieux permanents voit toutes ses paires classées {@link Level#AMBIGUOUS} (« plusieurs candidats »).
 */
@Service
public class PlaceMatchReportService {

    private final PlaceMatchRepository repository;

    public PlaceMatchReportService(PlaceMatchRepository repository) {
        this.repository = repository;
    }

    /** Paire classée. {@code reason} explique un classement ambigu dû à plusieurs candidats. */
    public record Match(CandidatePair pair, Level level, long distanceMeters, String reason) {
    }

    /** Totaux par niveau, nombre de lieux d'événement et d'activités concernés, paires (au plus {@code limit}). */
    public record Report(Map<Level, Integer> pairsByLevel, Map<Level, Integer> eventLocationsByLevel,
            Map<Level, Integer> activitiesByLevel, List<Match> matches) {
    }

    public Report report(String department, Level onlyLevel, int limit) {
        List<Match> classified = new ArrayList<>();
        for (CandidatePair pair : repository.findCandidatePairs(department, PlaceMatcher.MAX_DISTANCE_METERS)) {
            Level level = PlaceMatcher.compare(pair.eventLocation(), pair.place());
            if (level != Level.NONE) {
                long distance = Math.round(PlaceMatcher.distanceMeters(pair.eventLocation(), pair.place()));
                classified.add(new Match(pair, level, distance, null));
            }
        }
        Map<Long, Long> strongMatchesByEventLocation = new HashMap<>();
        classified.stream().filter(match -> match.level() != Level.AMBIGUOUS)
                .forEach(match -> strongMatchesByEventLocation.merge(match.pair().eventLocationId(), 1L, Long::sum));
        List<Match> matches = classified.stream()
                .map(match -> match.level() != Level.AMBIGUOUS
                        && strongMatchesByEventLocation.get(match.pair().eventLocationId()) > 1
                        ? new Match(match.pair(), Level.AMBIGUOUS, match.distanceMeters(), "plusieurs candidats")
                        : match)
                .sorted(Comparator.comparing(Match::level).thenComparing(match -> match.pair().eventLocationId()))
                .toList();

        Map<Level, Integer> pairs = new EnumMap<>(Level.class);
        Map<Level, Integer> eventLocations = new EnumMap<>(Level.class);
        Map<Level, Integer> activities = new EnumMap<>(Level.class);
        Map<Level, Set<Long>> seen = new EnumMap<>(Level.class);
        for (Match match : matches) {
            pairs.merge(match.level(), 1, Integer::sum);
            if (seen.computeIfAbsent(match.level(), level -> new HashSet<>())
                    .add(match.pair().eventLocationId())) {
                eventLocations.merge(match.level(), 1, Integer::sum);
                activities.merge(match.level(), match.pair().activityCount(), Integer::sum);
            }
        }
        List<Match> listed = matches.stream()
                .filter(match -> onlyLevel == null || match.level() == onlyLevel)
                .limit(limit)
                .toList();
        return new Report(pairs, eventLocations, activities, listed);
    }
}
