package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.collector.domain.Collector;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Collecteur DATAtourisme via data.gouv.fr (voir
 * {@code docs/02_Architecture/DATAGOUV_SOURCES.md}). Lit l'export CSV
 * simplifié quotidien publié par ADN Tourisme sur data.gouv.fr (jeu
 * {@code 5b598be088ee387c0c353714}) et n'en retient que les événements
 * (type {@code EntertainmentAndEvent}, « FMA »).
 *
 * <p>Configuration portée par la {@code Source} (pas de clé API) :
 * <ul>
 *   <li>{@code url} : URL stable de la ressource, de la forme
 *       {@code https://www.data.gouv.fr/api/1/datasets/r/{resourceId}} —
 *       data.gouv.fr redirige vers le fichier du jour (l'URL
 *       {@code static.data.gouv.fr} change à chaque export) ; le client
 *       doit donc suivre les redirections (voir
 *       {@link DataTourismeCollectorFactory}) ;</li>
 *   <li>{@code regionFilter}, optionnel : liste de départements séparés
 *       par des virgules (ex. {@code "84,13"}), comparés au début du code
 *       postal. Vide : tous les événements du fichier.</li>
 * </ul>
 *
 * <p>Format du CSV (constaté sur l'export réel du 13/10/2026) : UTF-8,
 * séparateur virgule, champs entre guillemets doubles si nécessaire
 * (retours à la ligne possibles dans {@code Description}) ; dans un champ,
 * {@code |} sépare plusieurs objets, {@code #} les propriétés d'un objet,
 * {@code <>} plusieurs valeurs d'une même propriété. Colonnes exploitées :
 * {@code Nom_du_POI}, {@code Categories_de_POI}, {@code Latitude},
 * {@code Longitude}, {@code Adresse_postale}, {@code Code_postal_et_commune}
 * ({@code 84000#Avignon}), {@code Periodes_regroupees}
 * ({@code 2026-10-16<->2026-10-25|...}), {@code Contacts_du_POI}
 * ({@code libellé#url<>url|...}), {@code Description},
 * {@code URI_ID_du_POI}.
 *
 * <p>Décisions :
 * <ul>
 *   <li>les périodes ne portent que des dates : chaque période devient un
 *       {@link CollectedTiming} de {@code 00:00} le premier jour à
 *       {@code 23:59} le dernier ;</li>
 *   <li>{@code externalId} = URI DATAtourisme (stable d'un export à
 *       l'autre) ; une URI répétée dans le fichier n'est retenue qu'une
 *       fois ;</li>
 *   <li>catégorie et tags : libellés français des types DATAtourisme
 *       connus ({@link #TYPE_LABELS}), pour être reconnus par le
 *       référentiel de mots-clés de classification ; les types génériques
 *       ou non traduits sont ignorés ;</li>
 *   <li>{@code sourceUrl} = premier site web des contacts, à défaut l'URI
 *       DATAtourisme ;</li>
 *   <li>événements entièrement passés ou sans coordonnées ignorés, comme
 *       pour {@link OpenAgendaCollector}.</li>
 * </ul>
 *
 * <p>Le fichier est lu en flux (le fichier national dépasse 50 Mo) : seuls
 * les événements retenus sont gardés en mémoire.
 */
public class DataTourismeCollector implements Collector {

    private static final String EVENT_TYPE = "EntertainmentAndEvent";
    private static final String LINK_TYPE_WEBSITE = "WEBSITE";
    private static final String COUNTRY_CODE = "FR";
    private static final ZoneId METROPOLE_ZONE = ZoneId.of("Europe/Paris");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    /** Fuseaux des DROM, par préfixe de code postal ; métropole et Corse par défaut. */
    private static final Map<String, String> OVERSEAS_TIMEZONES = Map.of(
            "971", "America/Guadeloupe",
            "972", "America/Martinique",
            "973", "America/Cayenne",
            "974", "Indian/Reunion",
            "976", "Indian/Mayotte");

    /**
     * Libellés français des types d'événement DATAtourisme (ontologie
     * {@code https://www.datatourisme.fr/ontology/core#}), du plus
     * spécifique au plus général : le premier type présent devient la
     * catégorie.
     */
    private static final Map<String, String> TYPE_LABELS = orderedLabels(
            "Opera", "opéra",
            "Concert", "concert",
            "Carnival", "carnaval",
            "ScreeningEvent", "projection",
            "VisualArtsEvent", "arts visuels",
            "ArtistSigning", "dédicace",
            "Practice", "atelier",
            "TheaterEvent", "théâtre",
            "Festival", "festival",
            "Exhibition", "exposition",
            "Market", "marché",
            "GarageSale", "vide-greniers",
            "BricABrac", "brocante",
            "FairOrShow", "foire",
            "Conference", "conférence",
            "Congress", "congrès",
            "Tasting", "dégustation",
            "Visit", "visite guidée",
            "Parade", "défilé",
            "TraditionalCelebration", "fête traditionnelle",
            "Commemoration", "commémoration",
            "OpenDay", "portes ouvertes",
            "Game", "jeu",
            "SportsCompetition", "compétition sportive",
            "SportsEvent", "sport",
            "ShowEvent", "spectacle",
            "ReligiousEvent", "événement religieux",
            "LocalAnimation", "animation",
            "SocialEvent", "fête",
            "SaleEvent", "vente",
            "BusinessEvent", "salon professionnel",
            "CulturalEvent", "culture");

    private final RestClient restClient;
    private final String resourceUrl;
    private final String sourceName;
    private final List<String> departments;
    private final Clock clock;

    DataTourismeCollector(RestClient.Builder builder, String resourceUrl, String sourceName, String regionFilter) {
        this(builder, resourceUrl, sourceName, regionFilter, Clock.systemUTC());
    }

    DataTourismeCollector(
            RestClient.Builder builder, String resourceUrl, String sourceName, String regionFilter, Clock clock) {
        this.restClient = builder.build();
        this.resourceUrl = resourceUrl;
        this.sourceName = sourceName;
        this.departments = parseDepartments(regionFilter);
        this.clock = clock;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public List<CollectedActivity> collect() {
        if (resourceUrl == null || resourceUrl.isBlank()) {
            throw new CollectorException(
                    "L'URL de la ressource DATAtourisme doit être configurée pour collecter.", null);
        }
        try {
            List<CollectedActivity> activities = restClient.get()
                    .uri(resourceUrl)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new CollectorException(
                                    "Réponse HTTP " + response.getStatusCode().value() + " pour " + resourceUrl, null);
                        }
                        try (Reader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            return parse(reader);
                        }
                    });
            return activities == null ? List.of() : activities;
        } catch (RestClientException exception) {
            throw new CollectorException("Échec de la collecte depuis DATAtourisme (data.gouv.fr).", exception);
        }
    }

    private List<CollectedActivity> parse(Reader reader) throws IOException {
        CsvReader csv = new CsvReader(reader);
        List<String> header = csv.next();
        if (header == null) {
            return List.of();
        }
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).replace("\uFEFF", "").trim(), i);
        }
        LocalDate today = LocalDate.now(clock.withZone(METROPOLE_ZONE));
        Set<String> seenUris = new HashSet<>();
        List<CollectedActivity> activities = new ArrayList<>();
        List<String> values;
        while ((values = csv.next()) != null) {
            Row row = new Row(columns, values);
            CollectedActivity activity = toCollectedActivity(row, today);
            if (activity != null && seenUris.add(activity.externalId())) {
                activities.add(activity);
            }
        }
        return activities;
    }

    /** {@code null} si la ligne n'est pas un événement à retenir (voir la javadoc de la classe). */
    private CollectedActivity toCollectedActivity(Row row, LocalDate today) {
        String uri = row.get("URI_ID_du_POI");
        List<String> types = types(row.get("Categories_de_POI"));
        if (isBlank(uri) || !types.contains(EVENT_TYPE)) {
            return null;
        }
        String[] postalCodeAndCity = firstObject(row.get("Code_postal_et_commune")).split("#", -1);
        String postalCode = postalCodeAndCity[0].trim();
        if (!matchesDepartments(postalCode)) {
            return null;
        }
        Double latitude = parseDouble(row.get("Latitude"));
        Double longitude = parseDouble(row.get("Longitude"));
        if (latitude == null || longitude == null) {
            return null;
        }
        List<CollectedTiming> timings = timings(row.get("Periodes_regroupees"));
        boolean upcoming = timings.stream().anyMatch(timing -> !timing.end().toLocalDate().isBefore(today));
        if (!upcoming) {
            return null;
        }
        String city = postalCodeAndCity.length > 1 ? postalCodeAndCity[1].trim() : null;
        List<String> tags = TYPE_LABELS.keySet().stream()
                .filter(types::contains)
                .map(TYPE_LABELS::get)
                .toList();
        List<CollectedLink> links = links(row.get("Contacts_du_POI"));
        String sourceUrl = links.isEmpty() ? uri : links.get(0).value();
        CollectedLocation location = new CollectedLocation(
                null,
                emptyToNull(row.get("Adresse_postale")),
                emptyToNull(city),
                emptyToNull(postalCode),
                null,
                COUNTRY_CODE,
                latitude,
                longitude,
                timezone(postalCode));
        return new CollectedActivity(
                row.get("Nom_du_POI").trim(),
                emptyToNull(row.get("Description")),
                tags.isEmpty() ? null : tags.get(0),
                sourceUrl,
                uri.trim(),
                sourceName,
                null,
                null,
                null,
                null,
                location,
                timings,
                null,
                List.of(),
                links,
                null,
                tags,
                null);
    }

    private boolean matchesDepartments(String postalCode) {
        return departments.isEmpty() || departments.stream().anyMatch(postalCode::startsWith);
    }

    /** Noms locaux des types ({@code https://…/core#Concert} → {@code Concert}). */
    private List<String> types(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .map(type -> type.substring(type.lastIndexOf('#') + 1).trim())
                .toList();
    }

    /** {@code 2026-10-16<->2026-10-25|2026-11-01<->2026-11-01}, trié par date de début. */
    private List<CollectedTiming> timings(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        List<CollectedTiming> timings = new ArrayList<>();
        for (String period : value.split("\\|")) {
            String[] bounds = period.split("<->", -1);
            try {
                LocalDate begin = LocalDate.parse(bounds[0].trim());
                LocalDate end = bounds.length > 1 && !bounds[1].isBlank() ? LocalDate.parse(bounds[1].trim()) : begin;
                timings.add(new CollectedTiming(begin.atStartOfDay(), end.atTime(END_OF_DAY)));
            } catch (DateTimeParseException exception) {
                // Période illisible : ignorée, les autres périodes de l'événement restent exploitables.
            }
        }
        timings.sort(Comparator.comparing(CollectedTiming::begin));
        return timings;
    }

    /** {@code libellé#url<>url|libellé#url} : un lien par URL web, dans l'ordre, sans doublon. */
    private List<CollectedLink> links(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        Map<String, String> labelsByUrl = new LinkedHashMap<>();
        for (String contact : value.split("\\|")) {
            int separator = contact.indexOf('#');
            String label = separator > 0 ? contact.substring(0, separator).trim() : null;
            String urls = separator >= 0 ? contact.substring(separator + 1) : contact;
            for (String url : urls.split("<>")) {
                String trimmed = url.trim();
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    labelsByUrl.putIfAbsent(trimmed, label);
                }
            }
        }
        List<CollectedLink> links = new ArrayList<>();
        labelsByUrl.forEach((url, label) -> links.add(new CollectedLink(LINK_TYPE_WEBSITE, label, url)));
        return links;
    }

    private String timezone(String postalCode) {
        if (postalCode.length() >= 3) {
            String overseas = OVERSEAS_TIMEZONES.get(postalCode.substring(0, 3));
            if (overseas != null) {
                return overseas;
            }
        }
        return METROPOLE_ZONE.getId();
    }

    private static List<String> parseDepartments(String regionFilter) {
        if (regionFilter == null || regionFilter.isBlank()) {
            return List.of();
        }
        return Arrays.stream(regionFilter.split(","))
                .map(String::trim)
                .filter(department -> !department.isEmpty())
                .toList();
    }

    private static Map<String, String> orderedLabels(String... typesAndLabels) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < typesAndLabels.length; i += 2) {
            labels.put(typesAndLabels[i], typesAndLabels[i + 1]);
        }
        return labels;
    }

    private String firstObject(String value) {
        return value == null ? "" : value.split("\\|", -1)[0];
    }

    private Double parseDouble(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Ligne CSV accessible par nom de colonne ; colonne absente ou ligne courte → chaîne vide. */
    private record Row(Map<String, Integer> columns, List<String> values) {

        String get(String column) {
            Integer index = columns.get(column);
            return index == null || index >= values.size() ? "" : values.get(index);
        }
    }

    /**
     * Lecteur CSV minimal (RFC 4180) : virgule, guillemets doubles,
     * guillemet doublé pour échapper, retours à la ligne dans un champ
     * entre guillemets. Évite une dépendance pour ce seul format.
     */
    static final class CsvReader {

        private final Reader reader;
        private int pending = -2;

        CsvReader(Reader reader) {
            this.reader = reader;
        }

        /** Prochain enregistrement, {@code null} en fin de fichier. */
        List<String> next() throws IOException {
            int c = read();
            while (c == '\r' || c == '\n') {
                c = read();
            }
            if (c == -1) {
                return null;
            }
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            while (true) {
                if (quoted) {
                    if (c == -1) {
                        fields.add(field.toString());
                        return fields;
                    }
                    if (c == '"') {
                        int following = read();
                        if (following == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            c = following;
                            continue;
                        }
                    } else {
                        field.append((char) c);
                    }
                } else if (c == '"' && field.isEmpty()) {
                    quoted = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n' || c == '\r' || c == -1) {
                    if (c == '\r') {
                        int following = read();
                        if (following != '\n') {
                            pending = following;
                        }
                    }
                    fields.add(field.toString());
                    return fields;
                } else {
                    field.append((char) c);
                }
                c = read();
            }
        }

        private int read() throws IOException {
            if (pending != -2) {
                int value = pending;
                pending = -2;
                return value;
            }
            return reader.read();
        }
    }
}
