package com.locallife.backend.collector.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedMedia;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Complète les événements du CSV DATAtourisme avec l'API DATAtourisme ({@code api.datatourisme.fr}, clé
 * {@code datatourisme.api-key}). Le CSV de data.gouv.fr ne porte que la description courte : la description
 * détaillée (par exemple la programmation d'un cinéma, séance par séance), les tarifs, les images, le téléphone
 * et les horaires en clair n'y sont pas. Ils sont dans l'API, pour les mêmes fiches.
 *
 * <p>Jointure par URI : le champ {@code uri} de l'API est l'URI DATAtourisme du CSV ({@code URI_ID_du_POI}),
 * déjà {@code externalId} des activités. Rien ne change donc dans l'identité des activités importées.
 *
 * <p>Lecture : seulement les fiches des événements retenus du CSV, par leur URI ({@code GET /v1/catalog} avec
 * {@code filters=uri[in]=…}), par lots de {@value #BATCH_SIZE}. Le nombre de requêtes suit donc le nombre
 * d'événements importés, quel que soit le fichier (régional, national filtré par une sélection) et que la source
 * ait ou non un filtre de départements : PACA (≈ 2 350 événements) tient en ≈ 47 requêtes. Quotas documentés
 * (19/10/2026) : 1 000 requêtes par heure, ~10 par seconde ; au-delà de {@value #MAX_REQUESTS} requêtes pour une
 * source, l'enrichissement est sauté (journalisé) plutôt que d'entamer le quota des autres sources.
 *
 * <p><b>Au mieux</b> : une panne, un refus ou une réponse illisible de l'API n'interrompt pas l'import. Les
 * événements restent ceux du CSV, sans complément, et un avertissement est journalisé.
 *
 * <p>Ce qui est complété ({@link #merge}) :
 * <ul>
 *   <li>{@code longDescription} : description détaillée, suivie des horaires en clair
 *       ({@code textOpeningHoursSpecification}) quand la source en donne ;</li>
 *   <li>{@code conditions} : tarifs ({@link #priceText}), qui servent aussi à l'indicateur gratuit/payant
 *       ({@code PriceIndicator}) ;</li>
 *   <li>images ({@code hasRepresentation}) si le CSV n'en a pas ;</li>
 *   <li>liens : site et téléphone ajoutés à ceux du CSV, sans doublon ;</li>
 *   <li>description courte de l'API si le CSV n'en a pas.</li>
 * </ul>
 */
public class DataTourismeApiEnricher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataTourismeApiEnricher.class);

    static final String DEFAULT_BASE_URL = "https://api.datatourisme.fr/v1/catalog";
    /** Préfixe des URI DATAtourisme ({@code externalId} des événements du CSV). */
    static final String URI_PREFIX = "https://data.datatourisme.fr/";
    static final String FIELDS =
            "uri,hasDescription,offers,hasRepresentation,hasContact,isLocatedAt.textOpeningHoursSpecification";
    /** URI par requête : ≈ 4 Ko d'adresse, sous les limites usuelles des serveurs et proxys. */
    static final int BATCH_SIZE = 50;
    /** Une page couvre tout un lot (pages de 100 au plus). */
    static final int PAGE_SIZE = 100;
    /** Garde-fou par source : au-delà, le quota horaire (1 000 requêtes) serait menacé. */
    static final int MAX_REQUESTS = 400;
    static final int MAX_ATTEMPTS = 3;
    static final int MAX_IMAGES = 5;
    /** Limite de {@code long_description} imposée par la normalisation des activités. */
    static final int MAX_LONG_DESCRIPTION = 10_000;
    static final int MAX_CONDITIONS = 2_000;

    private static final String LANG = "@fr";
    private static final String LINK_TYPE_WEBSITE = "WEBSITE";
    private static final String LINK_TYPE_PHONE = "PHONE";

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final Duration pause;
    private final Consumer<Duration> sleeper;

    public DataTourismeApiEnricher(RestClient.Builder builder, String apiKey, ObjectMapper objectMapper) {
        this(builder, apiKey, DEFAULT_BASE_URL, objectMapper, Duration.ofMillis(200), DataTourismeApiEnricher::sleep);
    }

    DataTourismeApiEnricher(
            RestClient.Builder builder, String apiKey, String baseUrl, ObjectMapper objectMapper, Duration pause,
            Consumer<Duration> sleeper) {
        this.restClient = builder.build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.pause = pause;
        this.sleeper = sleeper;
    }

    /** Vrai si une clé est configurée ; sinon l'import garde le seul CSV. */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Complète {@code activities} (événements du CSV) ; renvoie la liste d'origine si l'enrichissement est
     * désactivé, sauté ou en échec.
     */
    public List<CollectedActivity> enrich(List<CollectedActivity> activities) {
        if (!isEnabled() || activities.isEmpty()) {
            return activities;
        }
        List<String> uris = activities.stream()
                .map(CollectedActivity::externalId)
                .filter(uri -> uri != null && uri.startsWith(URI_PREFIX))
                .distinct()
                .toList();
        int requests = (uris.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        if (requests > MAX_REQUESTS) {
            LOGGER.warn("API DATAtourisme : {} événements ({} requêtes), au-delà du garde-fou de {} requêtes : "
                    + "enrichissement sauté (quota).", uris.size(), requests, MAX_REQUESTS);
            return activities;
        }
        Map<String, Details> details;
        try {
            details = fetch(uris);
        } catch (RuntimeException exception) {
            LOGGER.warn("API DATAtourisme indisponible, import limité au CSV : {}", exception.getMessage());
            return activities;
        }
        int enriched = 0;
        List<CollectedActivity> result = new ArrayList<>(activities.size());
        for (CollectedActivity activity : activities) {
            Details found = details.get(activity.externalId());
            if (found == null) {
                result.add(activity);
            } else {
                result.add(merge(activity, found));
                enriched++;
            }
        }
        LOGGER.info("API DATAtourisme : {} fiches lues en {} requêtes, {} événements sur {} complétés",
                details.size(), requests, enriched, activities.size());
        return result;
    }

    /** Fiches de l'API pour ces URI, par URI, en lots de {@value #BATCH_SIZE}. */
    Map<String, Details> fetch(List<String> uris) {
        Map<String, Details> details = new HashMap<>();
        for (int start = 0; start < uris.size(); start += BATCH_SIZE) {
            if (start > 0) {
                sleeper.accept(pause);
            }
            List<String> batch = uris.subList(start, Math.min(start + BATCH_SIZE, uris.size()));
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("filters", "uri[in]=" + String.join(",", batch))
                    .queryParam("fields", FIELDS)
                    .queryParam("lang", "fr")
                    .queryParam("page_size", PAGE_SIZE)
                    .encode()
                    .build()
                    .toUriString();
            for (JsonNode object : getPage(url).path("objects")) {
                String uri = object.path("uri").asText("");
                if (!uri.isBlank()) {
                    details.put(uri, details(object));
                }
            }
        }
        return details;
    }

    private JsonNode getPage(String url) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String body = restClient.get()
                        .uri(URI.create(url))
                        .header("X-API-Key", apiKey)
                        .retrieve()
                        .body(String.class);
                return objectMapper.readTree(body == null ? "{}" : body);
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                if (status != 429 && status < 500) {
                    throw new CollectorException("API DATAtourisme : réponse HTTP " + status, exception);
                }
                last = exception;
            } catch (RestClientException | java.io.IOException exception) {
                last = new CollectorException("API DATAtourisme : " + exception.getMessage(), exception);
            }
            sleeper.accept(Duration.ofSeconds(1L << attempt));
        }
        throw last;
    }

    static Details details(JsonNode object) {
        String shortDescription = null;
        String longDescription = null;
        for (JsonNode description : object.path("hasDescription")) {
            shortDescription = firstNonBlank(shortDescription, text(description.path("shortDescription")));
            longDescription = firstNonBlank(longDescription, text(description.path("description")));
        }
        String openingHours = null;
        for (JsonNode place : object.path("isLocatedAt")) {
            openingHours = firstNonBlank(openingHours, text(place.path("textOpeningHoursSpecification")));
        }
        List<CollectedMedia> images = new ArrayList<>();
        for (JsonNode representation : object.path("hasRepresentation")) {
            JsonNode annotation = representation.path("hasAnnotation").path(0);
            String title = text(annotation.path("title"));
            String credit = annotation.path("credits").path(0).asText(null);
            for (JsonNode resource : representation.path("hasRelatedResource")) {
                String locator = resource.path("locator").path(0).asText("");
                if (images.size() < MAX_IMAGES && isImage(resource, locator)) {
                    images.add(new CollectedMedia(locator, title, blankToNull(credit),
                            intOrNull(resource.path("width")), intOrNull(resource.path("height"))));
                }
            }
        }
        List<CollectedLink> links = new ArrayList<>();
        for (JsonNode contact : object.path("hasContact")) {
            for (JsonNode homepage : contact.path("homepage")) {
                String url = homepage.asText("").trim();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    links.add(new CollectedLink(LINK_TYPE_WEBSITE, null, url));
                }
            }
            for (JsonNode telephone : contact.path("telephone")) {
                String phone = telephone.asText("").trim();
                if (!phone.isEmpty()) {
                    links.add(new CollectedLink(LINK_TYPE_PHONE, null, phone));
                }
            }
        }
        return new Details(shortDescription, longDescription, openingHours, priceText(object.path("offers")),
                images, links);
    }

    /**
     * Tarifs en clair : le texte de la source ({@code textPriceSpecification}), sinon une phrase tirée des
     * montants ({@code priceSpecification.minPrice}/{@code maxPrice}) : « Gratuit. », « 12 € », « De 10 à 35 € ».
     */
    static String priceText(JsonNode offers) {
        List<String> texts = new ArrayList<>();
        BigDecimal min = null;
        BigDecimal max = null;
        for (JsonNode offer : offers) {
            String text = text(offer.path("textPriceSpecification"));
            if (text != null && !texts.contains(text)) {
                texts.add(text);
            }
            for (JsonNode specification : offer.path("priceSpecification")) {
                for (BigDecimal value : amounts(specification.path("minPrice"))) {
                    min = min == null || value.compareTo(min) < 0 ? value : min;
                    max = max == null || value.compareTo(max) > 0 ? value : max;
                }
                for (BigDecimal value : amounts(specification.path("maxPrice"))) {
                    min = min == null || value.compareTo(min) < 0 ? value : min;
                    max = max == null || value.compareTo(max) > 0 ? value : max;
                }
            }
        }
        if (!texts.isEmpty()) {
            return truncate(String.join("\n", texts), MAX_CONDITIONS);
        }
        if (max == null) {
            return null;
        }
        if (max.signum() == 0) {
            return "Gratuit.";
        }
        if (min.compareTo(max) == 0) {
            return euros(max) + ".";
        }
        return "De " + euros(min).replace(" €", "") + " à " + euros(max) + ".";
    }

    /** Événement du CSV complété par la fiche de l'API ; le CSV garde la main sur ce qu'il porte déjà. */
    static CollectedActivity merge(CollectedActivity activity, Details details) {
        String description = activity.description() != null ? activity.description() : details.shortDescription();
        String longDescription = details.longDescription();
        if (longDescription != null && longDescription.equals(description)) {
            longDescription = null;
        }
        if (details.openingHours() != null) {
            String hours = "Horaires : " + details.openingHours();
            longDescription = longDescription == null ? hours : longDescription + "\n\n" + hours;
        }
        longDescription = longDescription == null
                ? activity.longDescription()
                : truncate(longDescription, MAX_LONG_DESCRIPTION);
        String conditions = details.priceText() != null ? details.priceText() : activity.conditions();
        List<CollectedMedia> media = activity.media() == null || activity.media().isEmpty()
                ? details.images()
                : activity.media();
        List<CollectedLink> links = new ArrayList<>(activity.links() == null ? List.of() : activity.links());
        for (CollectedLink link : details.links()) {
            boolean known = links.stream().anyMatch(existing -> existing.value().equalsIgnoreCase(link.value()));
            if (!known) {
                links.add(link);
            }
        }
        String sourceUrl = activity.sourceUrl();
        if (sourceUrl != null && sourceUrl.equals(activity.externalId())) {
            sourceUrl = links.stream()
                    .filter(link -> LINK_TYPE_WEBSITE.equals(link.type()))
                    .map(CollectedLink::value)
                    .findFirst()
                    .orElse(sourceUrl);
        }
        return new CollectedActivity(
                activity.title(),
                description,
                activity.category(),
                sourceUrl,
                activity.externalId(),
                activity.source(),
                longDescription,
                conditions,
                activity.ageMin(),
                activity.ageMax(),
                activity.location(),
                activity.timings(),
                activity.recurrenceRule(),
                media,
                List.copyOf(links),
                activity.accessibility(),
                activity.tags(),
                activity.registrationInfo());
    }

    private static boolean isImage(JsonNode resource, String locator) {
        if (!locator.startsWith("http://") && !locator.startsWith("https://")) {
            return false;
        }
        for (JsonNode mime : resource.path("hasMimeType")) {
            String label = text(mime.path("label"));
            if (label != null) {
                return label.startsWith("image/");
            }
        }
        return locator.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png|gif|webp)(\\?.*)?$");
    }

    private static List<BigDecimal> amounts(JsonNode node) {
        List<BigDecimal> values = new ArrayList<>();
        for (JsonNode value : node.isArray() ? node : List.of(node)) {
            if (value.isNumber()) {
                values.add(value.decimalValue());
            } else if (value.isTextual()) {
                try {
                    values.add(new BigDecimal(value.asText().trim().replace(',', '.')));
                } catch (NumberFormatException exception) {
                    // Montant illisible : ignoré.
                }
            }
        }
        return values;
    }

    private static String euros(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        String text = stripped.scale() <= 0
                ? stripped.toPlainString()
                : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        return text + " €";
    }

    /** Texte français d'un champ multilingue ({@code {"@fr": "…"}}), retours à la ligne normalisés. */
    private static String text(JsonNode node) {
        JsonNode value = node.path(LANG);
        if (value.isArray()) {
            value = value.path(0);
        }
        String text = value.asText("").replace("\r\n", "\n").replace('\r', '\n').strip();
        return text.isEmpty() ? null : text;
    }

    private static Integer intOrNull(JsonNode node) {
        JsonNode value = node.isArray() ? node.path(0) : node;
        return value.isNumber() ? value.intValue() : null;
    }

    private static String firstNonBlank(String current, String candidate) {
        return current != null ? current : candidate;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1).stripTrailing() + "…";
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CollectorException("API DATAtourisme : interruption.", exception);
        }
    }

    /** Ce que l'API ajoute à un événement du CSV. */
    record Details(
            String shortDescription,
            String longDescription,
            String openingHours,
            String priceText,
            List<CollectedMedia> images,
            List<CollectedLink> links) {
    }
}
