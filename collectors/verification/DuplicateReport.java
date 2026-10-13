import com.locallife.backend.collector.application.CrossSourceDuplicateMatcher;
import com.locallife.backend.collector.application.CrossSourceDuplicateMatcher.Candidate;
import com.locallife.backend.collector.application.CrossSourceDuplicateMatcher.Level;
import com.locallife.backend.place.domain.CoordinatePrecisionPolicy;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Rapport de doublons entre sources (ADR-0011, phase D) — MESURE SANS ÉCRITURE.
 *
 * <p>Usage, depuis la racine du dépôt, après {@code mvn -q compile} dans {@code backend} :
 * <pre>
 *   java --class-path backend/target/classes collectors/verification/DuplicateReport.java evenements.csv sortie
 *   java --class-path backend/target/classes collectors/verification/DuplicateReport.java --self-test
 * </pre>
 *
 * <p>Entrée : CSV {@code id,source,title,periods,latitude,longitude,address,city,urls} ({@code periods} :
 * {@code début/fin} séparés par {@code ;}, dates ISO ; {@code urls} séparées par des espaces). Produit par
 * {@code events_for_matching.py} (DATAtourisme) ou par la requête de {@code VERIFICATION_DOUBLONS.md}.
 *
 * <p>Règle : celle de l'application, appelée telle quelle ({@link CrossSourceDuplicateMatcher#compare}) ; la
 * position est approximative si l'adresse est générique ({@link CoordinatePrecisionPolicy#isGenericPlaceText}).
 * Seules sont comparées les paires de sources différentes ayant un jour commun.
 *
 * <p>Classement de chaque paire retenue :
 * <ul>
 *   <li><b>CERTAINE</b> : les deux événements partagent un lien distinctif (même URL complète, avec un chemin,
 *       portée par un seul événement dans chaque source). Preuve indépendante du titre et de la position :
 *       elle sert de vérité de terrain ;</li>
 *   <li><b>PROBABLE</b> : niveau PROBABLE de la règle, sans lien distinctif, et aucun des deux événements n'a
 *       d'autre candidat dans l'autre source ;</li>
 *   <li><b>AMBIGUË</b> : niveau POSSIBLE, ou PROBABLE avec plusieurs candidats pour un même événement.</li>
 * </ul>
 *
 * <p>Mesures : <b>faux positifs certains</b> (paire PROBABLE alors que l'un des deux événements a une paire
 * CERTAINE avec un autre) ; <b>rappel</b> de la règle sur les paires CERTAINES (et motif des manques) ;
 * échantillon reproductible (graine fixe) de 20 paires par classe, avec une colonne de verdict à remplir.
 * Sorties : {@code sortie-paires.csv}, {@code sortie-echantillon.csv}. Aucune base, aucun réseau.
 */
public final class DuplicateReport {

    static final int SAMPLE_SIZE = 20;
    static final long SEED = 20261013L;

    record Event(String id, String source, String title, Set<LocalDate> days, Double latitude, Double longitude,
            String address, String city, Set<String> urls) {

        Candidate candidate() {
            boolean approximate = CoordinatePrecisionPolicy.isGenericPlaceText(address, city);
            return new Candidate(title, days, latitude, longitude, city, approximate);
        }
    }

    record Pair(Event first, Event second, Level level, boolean sharedLink, double distance) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--self-test")) {
            selfTest();
            return;
        }
        if (args.length != 2) {
            System.err.println("Usage : DuplicateReport.java evenements.csv prefixe-de-sortie | --self-test");
            System.exit(2);
        }
        List<Event> events = read(Path.of(args[0]));
        Map<String, List<Pair>> classes = classify(pairs(events));
        report(events, classes, args[1]);
    }

    // ---------------------------------------------------------------- lecture

    static List<Event> read(Path path) throws IOException {
        List<List<String>> rows = parseCsv(Files.readString(path, StandardCharsets.UTF_8));
        List<String> header = rows.get(0);
        List<Event> events = new ArrayList<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            Map<String, String> v = new HashMap<>();
            for (int i = 0; i < header.size() && i < row.size(); i++) {
                v.put(header.get(i), row.get(i));
            }
            events.add(new Event(v.get("id"), v.get("source"), v.get("title"), days(v.get("periods")),
                    number(v.get("latitude")), number(v.get("longitude")), v.get("address"), v.get("city"),
                    urls(v.get("urls"))));
        }
        return events;
    }

    static Set<LocalDate> days(String periods) {
        Set<LocalDate> days = new HashSet<>();
        if (periods == null || periods.isBlank()) {
            return days;
        }
        for (String period : periods.split(";")) {
            String[] bounds = period.trim().split("/");
            LocalDate start = LocalDate.parse(bounds[0].trim());
            LocalDate end = bounds.length > 1 ? LocalDate.parse(bounds[1].trim()) : start;
            for (LocalDate d = start; !d.isAfter(end) && days.size() < 400; d = d.plusDays(1)) {
                days.add(d);
            }
        }
        return days;
    }

    static Double number(String value) {
        return value == null || value.isBlank() ? null : Double.valueOf(value.trim());
    }

    /** URL normalisée : sans schéma, « www. », paramètres de suivi ni barre finale ; vide si sans chemin. */
    static Set<String> urls(String value) {
        Set<String> result = new HashSet<>();
        if (value == null) {
            return result;
        }
        for (String raw : value.trim().split("\\s+")) {
            String url = raw.toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "")
                    .replaceAll("[?&](utm_[a-z]+|fbclid)=[^&]*", "").replaceAll("[/?#]+$", "");
            int slash = url.indexOf('/');
            if (slash > 0 && slash < url.length() - 1) {
                result.add(url);
            }
        }
        return result;
    }

    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString().replaceAll("\r$", ""));
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    // ---------------------------------------------------------------- calcul

    /** Paires de sources différentes ayant au moins un jour commun, comparées par la règle de l'application. */
    static List<Pair> pairs(List<Event> events) {
        Map<String, Integer> urlCount = new HashMap<>();
        for (Event e : events) {
            for (String url : e.urls()) {
                urlCount.merge(e.source() + " " + url, 1, Integer::sum);
            }
        }
        Map<LocalDate, List<Integer>> byDay = new HashMap<>();
        for (int i = 0; i < events.size(); i++) {
            for (LocalDate day : events.get(i).days()) {
                byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(i);
            }
        }
        Set<Long> seen = new HashSet<>();
        List<Pair> pairs = new ArrayList<>();
        for (List<Integer> sameDay : byDay.values()) {
            for (int a = 0; a < sameDay.size(); a++) {
                for (int b = a + 1; b < sameDay.size(); b++) {
                    int i = Math.min(sameDay.get(a), sameDay.get(b));
                    int j = Math.max(sameDay.get(a), sameDay.get(b));
                    Event first = events.get(i);
                    Event second = events.get(j);
                    if (first.source().equals(second.source()) || !seen.add((long) i * events.size() + j)) {
                        continue;
                    }
                    boolean shared = first.urls().stream().anyMatch(url -> second.urls().contains(url)
                            && urlCount.get(first.source() + " " + url) == 1
                            && urlCount.get(second.source() + " " + url) == 1);
                    Level level = CrossSourceDuplicateMatcher.compare(first.candidate(), second.candidate());
                    if (shared || level != Level.NONE) {
                        pairs.add(new Pair(first, second, level, shared,
                                distance(first.candidate(), second.candidate())));
                    }
                }
            }
        }
        pairs.sort(Comparator.comparing((Pair p) -> p.first().id()).thenComparing(p -> p.second().id()));
        return pairs;
    }

    static double distance(Candidate a, Candidate b) {
        if (a.latitude() == null || b.latitude() == null) {
            return Double.POSITIVE_INFINITY;
        }
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
    }

    static Map<String, List<Pair>> classify(List<Pair> pairs) {
        Map<String, Integer> candidates = new HashMap<>();
        for (Pair p : pairs) {
            if (p.level() != Level.NONE) {
                candidates.merge(p.first().id(), 1, Integer::sum);
                candidates.merge(p.second().id(), 1, Integer::sum);
            }
        }
        Map<String, List<Pair>> classes = new LinkedHashMap<>();
        for (String name : List.of("CERTAINE", "PROBABLE", "AMBIGUE", "LIEN_SEUL")) {
            classes.put(name, new ArrayList<>());
        }
        for (Pair p : pairs) {
            if (p.sharedLink()) {
                classes.get("CERTAINE").add(p);
            } else if (p.level() == Level.PROBABLE && candidates.get(p.first().id()) == 1
                    && candidates.get(p.second().id()) == 1) {
                classes.get("PROBABLE").add(p);
            } else if (p.level() != Level.NONE) {
                classes.get("AMBIGUE").add(p);
            }
        }
        return classes;
    }

    /** Paires PROBABLE de la règle dont un événement a une paire CERTAINE avec un autre : faux positifs sûrs. */
    static List<Pair> certainFalsePositives(List<Pair> all, List<Pair> certain) {
        Map<String, String> partner = new HashMap<>();
        for (Pair p : certain) {
            partner.put(p.first().id(), p.second().id());
            partner.put(p.second().id(), p.first().id());
        }
        List<Pair> result = new ArrayList<>();
        for (Pair p : all) {
            if (p.level() == Level.PROBABLE && !p.sharedLink()) {
                String a = partner.get(p.first().id());
                String b = partner.get(p.second().id());
                if ((a != null && !a.equals(p.second().id())) || (b != null && !b.equals(p.first().id()))) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    /** Motif pour lequel la règle n'a pas retenu une paire CERTAINE. */
    static String missReason(Pair p) {
        Candidate a = p.first().candidate();
        Candidate b = p.second().candidate();
        Candidate sameTitle = new Candidate(a.title(), b.days(), b.latitude(), b.longitude(), b.city(),
                b.approximatePosition());
        if (CrossSourceDuplicateMatcher.compare(a, sameTitle) == Level.NONE) {
            return "lieu (distance " + (Double.isInfinite(p.distance()) ? "inconnue" : Math.round(p.distance()) + " m")
                    + ")";
        }
        return "titre";
    }

    // ---------------------------------------------------------------- sorties

    static void report(List<Event> events, Map<String, List<Pair>> classes, String prefix) throws IOException {
        List<Pair> all = new ArrayList<>();
        classes.values().forEach(all::addAll);
        List<Pair> certain = classes.get("CERTAINE");
        Map<String, Long> bySource = new TreeMap<>();
        events.forEach(e -> bySource.merge(e.source(), 1L, Long::sum));
        System.out.println("Événements : " + events.size() + " " + bySource);
        for (String name : List.of("CERTAINE", "PROBABLE", "AMBIGUE")) {
            System.out.println("  " + name + " : " + classes.get(name).size() + " paires");
        }
        long recalled = certain.stream().filter(p -> p.level() != Level.NONE).count();
        long recalledProbable = certain.stream().filter(p -> p.level() == Level.PROBABLE).count();
        System.out.printf(Locale.ROOT, "Rappel de la règle sur les paires CERTAINES : %d/%d retenues "
                + "(dont %d PROBABLE, %d POSSIBLE)%n", recalled, certain.size(), recalledProbable,
                recalled - recalledProbable);
        Map<String, Integer> reasons = new TreeMap<>();
        certain.stream().filter(p -> p.level() == Level.NONE).forEach(p -> reasons.merge(missReason(p)
                .replaceAll(" \\(.*", ""), 1, Integer::sum));
        System.out.println("  manquées, par motif : " + reasons);
        List<Pair> wrong = certainFalsePositives(all, certain);
        long probableTotal = all.stream().filter(p -> p.level() == Level.PROBABLE && !p.sharedLink()).count();
        System.out.println("Faux positifs certains parmi les " + probableTotal + " paires PROBABLE sans lien : "
                + wrong.size());
        wrong.stream().limit(5).forEach(p -> System.out.println("    " + p.first().title() + " | "
                + p.second().title() + " | " + p.first().city()));

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(prefix + "-paires.csv")))) {
            out.println("classe,niveau_regle,lien_partage,distance_m,source_1,id_1,titre_1,ville_1,source_2,id_2,"
                    + "titre_2,ville_2");
            for (Map.Entry<String, List<Pair>> e : classes.entrySet()) {
                for (Pair p : e.getValue()) {
                    out.println(line(e.getKey(), p, null));
                }
            }
        }
        Random random = new Random(SEED);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(prefix + "-echantillon.csv")))) {
            out.println("classe,niveau_regle,lien_partage,distance_m,source_1,id_1,titre_1,ville_1,source_2,id_2,"
                    + "titre_2,ville_2,verdict (MEME / DIFFERENT / INCERTAIN)");
            for (String name : List.of("CERTAINE", "PROBABLE", "AMBIGUE")) {
                List<Pair> pool = new ArrayList<>(classes.get(name));
                Collections.shuffle(pool, random);
                pool.stream().limit(SAMPLE_SIZE).forEach(p -> out.println(line(name, p, "")));
            }
        }
        System.out.println("Écrit : " + prefix + "-paires.csv, " + prefix + "-echantillon.csv");
    }

    static String line(String name, Pair p, String verdict) {
        List<String> cells = new ArrayList<>(List.of(name, p.level().name(), String.valueOf(p.sharedLink()),
                Double.isInfinite(p.distance()) ? "" : String.valueOf(Math.round(p.distance())),
                p.first().source(), p.first().id(), p.first().title(), String.valueOf(p.first().city()),
                p.second().source(), p.second().id(), p.second().title(), String.valueOf(p.second().city())));
        if (verdict != null) {
            cells.add(verdict);
        }
        return String.join(",", cells.stream().map(c -> "\"" + c.replace("\"", "\"\"") + "\"").toList());
    }

    // ---------------------------------------------------------------- autotest

    static void selfTest() {
        LocalDate d = LocalDate.of(2026, 10, 17);
        Set<LocalDate> day = Set.of(d);
        Event oa = new Event("oa1", "OpenAgenda", "Nuit des musées", day, 43.9500, 4.8070, "Rue de la République",
                "Avignon", urls("https://openagenda.com/x/events/nuit-des-musees?utm_source=fb"));
        Event dt = new Event("dt1", "DATAtourisme", "Nuit des Musées !", day, 43.9501, 4.8071,
                "Rue de la République", "Avignon", urls("http://www.openagenda.com/x/events/nuit-des-musees/"));
        Event far = new Event("dt2", "DATAtourisme", "Nuit des musées", day, 43.9600, 4.8200, "Place Pie",
                "Avignon", Set.of());
        Event other = new Event("oa2", "OpenAgenda", "Concert", day, 43.9300, 4.8000, "Divers lieux", "Avignon",
                Set.of());
        Event otherDt = new Event("dt3", "DATAtourisme", "Concert", day, 43.9310, 4.8010, "Centre ville", "Avignon",
                Set.of());
        Event shop = new Event("dt4", "DATAtourisme", "Jazz manouche", day, 43.9, 4.8, "", "Avignon",
                urls("https://www.theatre.fr/"));
        Event shop2 = new Event("oa3", "OpenAgenda", "Théâtre d'impro", day, 43.9, 4.8, "", "Avignon",
                urls("https://theatre.fr"));
        List<Pair> pairs = pairs(List.of(oa, dt, far, other, otherDt, shop, shop2));
        Map<String, List<Pair>> classes = classify(pairs);
        check(classes.get("CERTAINE").size() == 1, "lien distinctif partagé → CERTAINE (URL normalisée)");
        check(classes.get("PROBABLE").isEmpty(), "dt2 à plus de 300 m : pas de PROBABLE");
        check(classes.get("AMBIGUE").size() == 1, "titre générique en zone approximative → AMBIGUE");
        check(pairs.stream().noneMatch(p -> p.first().id().equals("dt4") || p.second().id().equals("dt4")),
                "une page d'accueil n'est pas un lien distinctif");
        List<Pair> wrong = certainFalsePositives(pairs, classes.get("CERTAINE"));
        check(wrong.isEmpty(), "dt2 est à plus de 300 m : pas de PROBABLE, donc pas de faux positif");
        Event close = new Event("dt5", "DATAtourisme", "Nuit des musées", day, 43.9502, 4.8072,
                "Rue de la République", "Avignon", Set.of());
        List<Pair> withClose = pairs(List.of(oa, dt, close));
        check(certainFalsePositives(withClose, classify(withClose).get("CERTAINE")).size() == 1,
                "PROBABLE vers un autre événement que le partenaire certain → faux positif certain");
        Event twin = new Event("dt7", "DATAtourisme", "Nuit des musées", day, 43.9501, 4.8069,
                "Rue de la République", "Avignon", Set.of());
        Event alone = new Event("oa4", "OpenAgenda", "Nuit des musées", day, 43.9500, 4.8070,
                "Rue de la République", "Avignon", Set.of());
        Map<String, List<Pair>> single = classify(pairs(List.of(alone, twin)));
        check(single.get("PROBABLE").size() == 1, "un seul candidat, titre identique, < 300 m → PROBABLE");
        Map<String, List<Pair>> multiple = classify(pairs(List.of(alone, twin, close)));
        check(multiple.get("PROBABLE").isEmpty() && multiple.get("AMBIGUE").size() == 2,
                "deux candidats PROBABLE pour un même événement → AMBIGUE");
        Event later = new Event("dt6", "DATAtourisme", "Nuit des musées", Set.of(d.plusDays(1)), 43.95, 4.807,
                "", "Avignon", Set.of());
        check(pairs(List.of(oa, later)).isEmpty(), "aucun jour commun → aucune paire");
        check(days("2026-10-01/2026-10-03;2026-10-10/2026-10-10").size() == 4, "périodes → jours");
        System.out.println("Autotest réussi : 10 vérifications.");
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Échec : " + message);
        }
    }
}
