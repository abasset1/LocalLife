package com.locallife.backend.place.application;

import com.locallife.backend.place.domain.EventLocationDuplicateMatcher;
import com.locallife.backend.place.domain.PlaceMatcher;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
import com.locallife.backend.place.infrastructure.EventLocationDuplicateRepository;
import com.locallife.backend.place.infrastructure.EventLocationDuplicateRepository.CandidatePair;
import com.locallife.backend.place.infrastructure.EventLocationDuplicateRepository.EventLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Rapport des doublons de lieux d'événement (ADR-0011, § 7.2.13) — MESURE SANS ÉCRITURE. Les doublons
 * {@link Level#CERTAIN} sont regroupés en grappes (un même lieu réel, plusieurs lignes) ; les doublons
 * {@link Level#PROBABLE} sont listés paire par paire, jamais regroupés par ricochet (« Château de Simiane »
 * relierait sinon le n° 5 et le n° 8 de la même place). Aucun lieu n'est modifié ni fusionné.
 */
@Service
public class EventLocationDuplicateReportService {

    private final EventLocationDuplicateRepository repository;

    public EventLocationDuplicateReportService(EventLocationDuplicateRepository repository) {
        this.repository = repository;
    }

    /**
     * Grappe certaine ou paire probable : lieux d'événement (le plus ancien en premier) et plus grande distance
     * entre deux d'entre eux.
     */
    public record Cluster(Level level, List<EventLocation> locations, long maxDistanceMeters) {

        public int activityCount() {
            return locations.stream().mapToInt(EventLocation::activityCount).sum();
        }
    }

    /**
     * Totaux par niveau : grappes (ou paires), lieux d'événement concernés, lignes en trop (lieux moins grappes ;
     * pour les paires probables, un maximum) et activités concernées ; grappes certaines puis paires probables,
     * des plus grandes aux plus petites (au plus {@code limit}).
     */
    public record Report(Map<Level, Integer> clustersByLevel, Map<Level, Integer> locationsByLevel,
            Map<Level, Integer> extraRowsByLevel, Map<Level, Integer> activitiesByLevel, List<Cluster> clusters) {
    }

    public Report report(String department, Level onlyLevel, int limit) {
        Map<Long, EventLocation> locations = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        List<CandidatePair> probable = new ArrayList<>();
        for (CandidatePair pair : repository.findCandidatePairs(department, PlaceMatcher.MAX_DISTANCE_METERS)) {
            Level level = EventLocationDuplicateMatcher.compare(pair.first().candidate(), pair.second().candidate());
            if (level == Level.CERTAIN) {
                locations.put(pair.first().id(), pair.first());
                locations.put(pair.second().id(), pair.second());
                union(parent, pair.first().id(), pair.second().id());
            } else if (level == Level.PROBABLE) {
                probable.add(pair);
            }
        }

        // Grappes certaines : seuls les doublons certains sont regroupés (pas de ricochet par un texte court).
        Map<Long, List<EventLocation>> members = new TreeMap<>();
        locations.values().forEach(location ->
                members.computeIfAbsent(find(parent, location.id()), root -> new ArrayList<>()).add(location));
        List<Cluster> clusters = new ArrayList<>();
        members.values().forEach(group -> clusters.add(cluster(Level.CERTAIN, group)));

        // Doublons probables : paire par paire, sauf entre deux lignes déjà dans la même grappe certaine.
        Set<String> seenPairs = new HashSet<>();
        for (CandidatePair pair : probable) {
            Long root1 = find(parent, pair.first().id());
            Long root2 = find(parent, pair.second().id());
            if (!root1.equals(root2) && seenPairs.add(Math.min(root1, root2) + "-" + Math.max(root1, root2))) {
                clusters.add(cluster(Level.PROBABLE, List.of(pair.first(), pair.second())));
            }
        }
        clusters.sort(Comparator.comparing(Cluster::level)
                .thenComparing(Comparator.comparing((Cluster cluster) -> cluster.locations().size()).reversed())
                .thenComparing(cluster -> cluster.locations().get(0).id()));

        Map<Level, Integer> clusterCounts = new EnumMap<>(Level.class);
        Map<Level, Integer> locationCounts = new EnumMap<>(Level.class);
        Map<Level, Integer> extraRows = new EnumMap<>(Level.class);
        Map<Level, Integer> activities = new EnumMap<>(Level.class);
        for (Cluster cluster : clusters) {
            clusterCounts.merge(cluster.level(), 1, Integer::sum);
            locationCounts.merge(cluster.level(), cluster.locations().size(), Integer::sum);
            extraRows.merge(cluster.level(), cluster.locations().size() - 1, Integer::sum);
            activities.merge(cluster.level(), cluster.activityCount(), Integer::sum);
        }
        List<Cluster> listed = clusters.stream()
                .filter(cluster -> onlyLevel == null || cluster.level() == onlyLevel)
                .limit(limit)
                .toList();
        return new Report(clusterCounts, locationCounts, extraRows, activities, listed);
    }

    private static Cluster cluster(Level level, List<EventLocation> group) {
        List<EventLocation> sorted = group.stream().sorted(Comparator.comparing(EventLocation::id)).toList();
        double max = 0;
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                max = Math.max(max, PlaceMatcher.distanceMeters(sorted.get(i).candidate(), sorted.get(j).candidate()));
            }
        }
        return new Cluster(level, sorted, Math.round(max));
    }

    private static Long find(Map<Long, Long> parent, Long id) {
        Long root = id;
        while (parent.containsKey(root) && !parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        return root;
    }

    private static void union(Map<Long, Long> parent, Long first, Long second) {
        parent.putIfAbsent(first, first);
        parent.putIfAbsent(second, second);
        Long a = find(parent, first);
        Long b = find(parent, second);
        if (!a.equals(b)) {
            parent.put(Math.max(a, b), Math.min(a, b));
        }
    }
}
