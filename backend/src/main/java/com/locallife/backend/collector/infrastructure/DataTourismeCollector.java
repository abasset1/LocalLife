package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.place.domain.CoordinatePrecisionPolicy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p><b>Fichier suspect</b> : {@link #collect()} lève une
 * {@link CollectorException} si le fichier est vide, si une colonne de
 * {@link #REQUIRED_COLUMNS} manque, si plus de 1 % des lignes n'ont pas le
 * nombre de champs de l'en-tête, ou si le fichier ne contient aucun
 * événement. {@code ImportService} n'archive alors rien (échec de collecte).
 * À l'inverse, un fichier valide dont aucun événement ne correspond aux
 * filtres (départements, dates) est un import vide légitime : la liste vide
 * est renvoyée et les activités disparues sont archivées normalement.
 *
 * <p>Décisions :
 * <ul>
 *   <li>les périodes ne portent que des dates : chaque période devient un
 *       {@link CollectedTiming} de {@code 00:00} le premier jour à
 *       {@code 23:59} le dernier ;</li>
 *   <li><b>période de validité</b> : une période de plus de
 *       {@value #MAX_OCCURRENCE_DAYS} jours, pour un type qui n'est pas
 *       continu par nature ({@link #CONTINUOUS_TYPES} : exposition,
 *       festival, salon), décrit la saison pendant laquelle l'événement a
 *       lieu, pas une occurrence (ex. un marché du jeudi publié du 1er
 *       janvier au 31 décembre). Le CSV ne dit pas quels jours : elle est
 *       écartée plutôt que présentée comme continue, et aucune récurrence
 *       n'est déduite. Un événement qui n'a que des périodes de validité
 *       n'est pas importé ;</li>
 *   <li><b>adresse générique</b> (« Divers lieux », « Centre ville »,
 *       « Dans le village »…) : reprise comme {@code name} du lieu, sans
 *       {@code address}. Les coordonnées de la source sont conservées
 *       (exigées par le modèle) mais ne désignent pas un lieu précis ; sans
 *       adresse, ce lieu n'est jamais fusionné par adresse avec un autre ;</li>
 *   <li>{@code externalId} = URI DATAtourisme (stable d'un export à
 *       l'autre) ; une URI répétée dans le fichier n'est retenue qu'une
 *       fois ;</li>
 *   <li>catégorie et tags : libellés français des types DATAtourisme
 *       connus ({@link #TYPE_LABELS}), pour être reconnus par le
 *       référentiel de mots-clés de classification ; les types génériques
 *       ou non traduits sont ignorés ;</li>
 *   <li>liens, par priorité : site de l'événement (contact sans libellé
 *       dans la source), puis sites de tiers (contact avec libellé :
 *       organisateur, lieu), puis billetterie ({@link #BOOKING}, sur le
 *       libellé ou l'URL) ; {@code sourceUrl} = premier lien de cet ordre,
 *       à défaut l'URI DATAtourisme ;</li>
 *   <li>événements entièrement passés ou sans coordonnées ignorés, comme
 *       pour {@link OpenAgendaCollector}.</li>
 * </ul>
 *
 * <p>Le fichier est lu en flux (le fichier national dépasse 50 Mo) : seuls
 * les événements retenus sont gardés en mémoire.
 */
public class DataTourismeCollector implements Collector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataTourismeCollector.class);

    /** Colonnes lues par ce collecteur ; l'absence d'une seule signale un changement de format. */
    static final List<String> REQUIRED_COLUMNS = List.of(
            "Nom_du_POI", "Categories_de_POI", "Latitude", "Longitude", "Adresse_postale",
            "Code_postal_et_commune", "Periodes_regroupees", "Contacts_du_POI", "Description", "URI_ID_du_POI");

    /** Part maximale de lignes dont le nombre de champs diffère de l'en-tête. */
    private static final double MAX_MALFORMED_RATIO = 0.01;

    /** Au-delà, une période d'un type non continu est une période de validité (voir la javadoc). */
    static final int MAX_OCCURRENCE_DAYS = 31;

    /** Types dont une longue période est bien continue (ouvert tous les jours de la période). */
    private static final Set<String> CONTINUOUS_TYPES = Set.of("Exhibition", "Festival", "FairOrShow");

    /** Billetterie, reconnue sur le libellé ou l'URL d'un contact. */
    private static final Pattern BOOKING = Pattern.compile(
            "reserv|billet|ticket|booking|shotgun|helloasso|weezevent|mapado", Pattern.CASE_INSENSITIVE);

    private static final String BOOKING_LABEL = "Réservation";

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
            throw new CollectorException("Fichier DATAtourisme vide : import interrompu.", null);
        }
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).replace("\uFEFF", "").trim(), i);
        }
        List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !columns.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new CollectorException("Format DATAtourisme inattendu, colonnes absentes : " + missing, null);
        }
        LocalDate today = LocalDate.now(clock.withZone(METROPOLE_ZONE));
        Set<String> seenUris = new HashSet<>();
        List<CollectedActivity> activities = new ArrayList<>();
        Map<String, Integer> rejected = new TreeMap<>();
        int rows = 0;
        int malformed = 0;
        int events = 0;
        List<String> values;
        while ((values = csv.next()) != null) {
            rows++;
            if (values.size() != header.size()) {
                malformed++;
                continue;
            }
            Row row = new Row(columns, values);
            if (!types(row.get("Categories_de_POI")).contains(EVENT_TYPE)) {
                continue;
            }
            events++;
            CollectedActivity activity = toCollectedActivity(row, today, rejected);
            if (activity == null) {
                continue;
            }
            if (seenUris.add(activity.externalId())) {
                activities.add(activity);
            } else {
                rejected.merge("URI en double", 1, Integer::sum);
            }
        }
        if (rows == 0) {
            throw new CollectorException(
                    "Fichier DATAtourisme sans aucune ligne de données : import interrompu.", null);
        }
        if (malformed > rows * MAX_MALFORMED_RATIO) {
            throw new CollectorException("Fichier DATAtourisme mal formé : " + malformed + " lignes sur " + rows
                    + " n'ont pas le nombre de colonnes de l'en-tête.", null);
        }
        if (events == 0) {
            throw new CollectorException("Aucun événement dans le fichier DATAtourisme (" + rows
                    + " lignes) : ressource ou format inattendu.", null);
        }
        LOGGER.info("DATAtourisme '{}' : {} lignes, {} événements, {} retenus, écartés : {}",
                sourceName, rows, events, activities.size(), rejected);
        return activities;
    }

    /**
     * {@code null} si l'événement n'est pas à retenir, la raison étant comptée dans {@code rejected}
     * (voir la javadoc de la classe).
     */
    private CollectedActivity toCollectedActivity(Row row, LocalDate today, Map<String, Integer> rejected) {
        String uri = row.get("URI_ID_du_POI");
        if (isBlank(uri)) {
            return reject(rejected, "sans URI");
        }
        List<String> types = types(row.get("Categories_de_POI"));
        String[] postalCodeAndCity = firstObject(row.get("Code_postal_et_commune")).split("#", -1);
        String postalCode = postalCodeAndCity[0].trim();
        if (!matchesDepartments(postalCode)) {
            return reject(rejected, "hors départements");
        }
        Double latitude = parseDouble(row.get("Latitude"));
        Double longitude = parseDouble(row.get("Longitude"));
        if (latitude == null || longitude == null) {
            return reject(rejected, "sans coordonnées");
        }
        List<Period> periods = periods(row.get("Periodes_regroupees"));
        boolean continuous = types.stream().anyMatch(CONTINUOUS_TYPES::contains);
        List<Period> occurrences = periods.stream()
                .filter(period -> continuous || period.days() <= MAX_OCCURRENCE_DAYS)
                .toList();
        if (occurrences.isEmpty()) {
            return reject(rejected, periods.isEmpty() ? "sans période lisible" : "période de validité seulement");
        }
        if (occurrences.stream().allMatch(period -> period.end().isBefore(today))) {
            return reject(rejected, "passé");
        }
        String city = postalCodeAndCity.length > 1 ? emptyToNull(postalCodeAndCity[1]) : null;
        List<String> tags = TYPE_LABELS.keySet().stream()
                .filter(types::contains)
                .map(TYPE_LABELS::get)
                .toList();
        List<CollectedLink> links = links(row.get("Contacts_du_POI"));
        String sourceUrl = links.isEmpty() ? uri.trim() : links.get(0).value();
        String address = emptyToNull(row.get("Adresse_postale"));
        String locationName = null;
        if (address != null && isGenericAddress(address, city)) {
            locationName = address;
            address = null;
        }
        CollectedLocation location = new CollectedLocation(
                locationName,
                address,
                city,
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
                occurrences.stream().map(Period::toTiming).toList(),
                null,
                List.of(),
                links,
                null,
                tags,
                null);
    }

    private static CollectedActivity reject(Map<String, Integer> rejected, String reason) {
        rejected.merge(reason, 1, Integer::sum);
        return null;
    }

    /**
     * Vrai si l'adresse désigne plusieurs lieux ou une zone (« Divers lieux », « Centre ville
     * Saint-Rémy-de-Provence ») plutôt qu'un lieu. Règle partagée avec la précision géographique
     * ({@link CoordinatePrecisionPolicy#isGenericPlaceText}, ADR-0011, phase A).
     */
    static boolean isGenericAddress(String address, String city) {
        return CoordinatePrecisionPolicy.isGenericPlaceText(address, city);
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
    private List<Period> periods(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        List<Period> periods = new ArrayList<>();
        for (String period : value.split("\\|")) {
            String[] bounds = period.split("<->", -1);
            try {
                LocalDate begin = LocalDate.parse(bounds[0].trim());
                LocalDate end = bounds.length > 1 && !bounds[1].isBlank() ? LocalDate.parse(bounds[1].trim()) : begin;
                periods.add(new Period(begin, end));
            } catch (DateTimeParseException exception) {
                // Période illisible : ignorée, les autres périodes de l'événement restent exploitables.
            }
        }
        periods.sort(Comparator.comparing(Period::begin));
        return periods;
    }

    /**
     * {@code libellé#url<>url|libellé#url} : un lien par URL web, sans doublon, triés par priorité
     * (site de l'événement, tiers, billetterie — voir la javadoc de la classe), ordre de la source
     * conservé à priorité égale.
     */
    private List<CollectedLink> links(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        Map<String, RankedLink> byUrl = new LinkedHashMap<>();
        for (String contact : value.split("\\|")) {
            int separator = contact.indexOf('#');
            String label = separator > 0 ? emptyToNull(contact.substring(0, separator)) : null;
            String urls = separator >= 0 ? contact.substring(separator + 1) : contact;
            for (String url : urls.split("<>")) {
                String trimmed = url.trim();
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    RankedLink candidate = new RankedLink(trimmed, label, rank(label, trimmed));
                    byUrl.merge(trimmed, candidate, (kept, other) -> other.rank() < kept.rank() ? other : kept);
                }
            }
        }
        return byUrl.values().stream()
                .sorted(Comparator.comparingInt(RankedLink::rank))
                .map(RankedLink::toLink)
                .toList();
    }

    /** 0 : site de l'événement (sans libellé) ; 1 : tiers (avec libellé) ; 2 : billetterie. */
    private static int rank(String label, String url) {
        if (BOOKING.matcher(url).find() || (label != null && BOOKING.matcher(label).find())) {
            return 2;
        }
        return label == null ? 0 : 1;
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

    /** Période de la source, bornes incluses. */
    private record Period(LocalDate begin, LocalDate end) {

        long days() {
            return ChronoUnit.DAYS.between(begin, end) + 1;
        }

        CollectedTiming toTiming() {
            return new CollectedTiming(begin.atStartOfDay(), end.atTime(END_OF_DAY));
        }
    }

    private record RankedLink(String url, String label, int rank) {

        CollectedLink toLink() {
            return new CollectedLink(LINK_TYPE_WEBSITE, label == null && rank == 2 ? BOOKING_LABEL : label, url);
        }
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
