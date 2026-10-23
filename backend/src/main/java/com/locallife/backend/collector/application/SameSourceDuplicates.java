package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Doublons publiés par une même source : plusieurs événements distincts à la source (identifiants externes
 * différents) mais identiques pour l'utilisateur. Mesure sur la bêta (22/10/2026) : un même atelier publié
 * 42 fois, mêmes horaires et même lieu, par un agenda OpenAgenda (voir
 * {@code infra/verification/doublons_detail.sql}).
 *
 * <p>Deux éléments sont des doublons s'ils ont la même signature : titre normalisé (casse, espaces), mêmes
 * horaires (tous les créneaux, début et fin) et même lieu (position et adresse). Des créneaux ou des lieux
 * différents sous un même titre (« Atelier senior 360 » dans 13 villes) restent des événements distincts.
 * Un élément sans titre, sans créneau ou sans lieu n'a pas de signature : il n'est jamais rapproché.
 *
 * <p>Fonction pure : ne lit ni n'écrit rien. Le choix de l'élément conservé revient à {@link ImportService}.
 */
final class SameSourceDuplicates {

    private SameSourceDuplicates() {
    }

    /**
     * Groupes de clés d'import dont les éléments ont la même signature, dans l'ordre de la collecte. Seuls
     * les groupes d'au moins deux clés distinctes sont renvoyés : une même clé collectée deux fois n'est
     * pas un doublon au sens de cette règle (elle désigne déjà une seule activité).
     */
    static List<List<String>> groups(List<CollectedActivity> items, Function<CollectedActivity, String> keyOf) {
        Map<String, Set<String>> keysBySignature = new LinkedHashMap<>();
        for (CollectedActivity item : items) {
            Optional<String> signature = signature(item);
            if (signature.isEmpty()) {
                continue;
            }
            String key = keyOf.apply(item);
            if (key != null) {
                keysBySignature.computeIfAbsent(signature.get(), ignored -> new LinkedHashSet<>()).add(key);
            }
        }
        List<List<String>> groups = new ArrayList<>();
        for (Set<String> keys : keysBySignature.values()) {
            if (keys.size() > 1) {
                groups.add(List.copyOf(keys));
            }
        }
        return groups;
    }

    /** Signature d'un élément, vide si un titre, un créneau ou un lieu manque. */
    static Optional<String> signature(CollectedActivity item) {
        String title = normalize(item.title());
        String timings = timings(item.timings());
        String place = place(item.location());
        if (title.isEmpty() || timings.isEmpty() || place.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(title + "|" + timings + "|" + place);
    }

    private static String timings(List<CollectedTiming> timings) {
        if (timings == null) {
            return "";
        }
        return timings.stream()
                .filter(Objects::nonNull)
                .filter(timing -> timing.begin() != null)
                .map(timing -> timing.begin() + "/" + timing.end())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private static String place(CollectedLocation location) {
        if (location == null) {
            return "";
        }
        String address = normalize(location.address()) + "," + normalize(location.city());
        boolean hasPosition = location.latitude() != null && location.longitude() != null;
        if (!hasPosition && ",".equals(address)) {
            return "";
        }
        return location.latitude() + "," + location.longitude() + "," + address;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
