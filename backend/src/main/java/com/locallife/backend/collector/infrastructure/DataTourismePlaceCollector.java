package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.CollectedPlace;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Collecteur des <b>lieux</b> d'un export CSV DATAtourisme publié sur data.gouv.fr (ADR-0011, phase B) : même
 * fichier et mêmes contrôles que {@link DataTourismeCollector} (événements), qui n'est pas modifié.
 *
 * <p>Renvoie tous les lieux du fichier situés dans les départements demandés, avec tous leurs types : la liste
 * blanche et la modération sont appliquées par l'import des lieux, qui doit aussi reconnaître un lieu connu dont
 * les types sortent de la liste.
 *
 * <p>Un lieu est une ligne qui porte {@code PlaceOfInterest} et aucun de {@code EntertainmentAndEvent}
 * (événement), {@code Product} ni {@code Tour}. Écartés et comptés : sans URI, sans coordonnées, hors
 * départements, URI en double.
 *
 * <p>Fichier suspect, collecte en échec (rien n'est écrit ni archivé) : fichier vide, colonne obligatoire absente,
 * plus de 1 % de lignes mal formées, aucun lieu, téléchargement interrompu.
 */
public class DataTourismePlaceCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataTourismePlaceCollector.class);

    private static final double MAX_MALFORMED_RATIO = 0.01;
    private static final String CORE_NAMESPACE = "https://www.datatourisme.fr/ontology/core#";
    private static final String PLACE_TYPE = "PlaceOfInterest";
    private static final Set<String> NOT_PLACE_TYPES = Set.of("EntertainmentAndEvent", "Product", "Tour");

    private final RestClient restClient;
    private final String resourceUrl;
    private final String sourceName;
    private final List<String> departments;

    DataTourismePlaceCollector(RestClient.Builder builder, String resourceUrl, String sourceName, String regionFilter) {
        this.restClient = builder.build();
        this.resourceUrl = resourceUrl;
        this.sourceName = sourceName;
        this.departments = regionFilter == null || regionFilter.isBlank()
                ? List.of()
                : Arrays.stream(regionFilter.split(",")).map(String::trim).filter(d -> !d.isEmpty()).toList();
    }

    public List<CollectedPlace> collect() {
        if (resourceUrl == null || resourceUrl.isBlank()) {
            throw new CollectorException(
                    "L'URL de la ressource DATAtourisme doit être configurée pour collecter.", null);
        }
        try {
            List<CollectedPlace> places = restClient.get()
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
            return places == null ? List.of() : places;
        } catch (RestClientException exception) {
            throw new CollectorException("Échec de la collecte des lieux DATAtourisme (data.gouv.fr).", exception);
        }
    }

    List<CollectedPlace> parse(Reader reader) throws IOException {
        DataTourismeCollector.CsvReader csv = new DataTourismeCollector.CsvReader(reader);
        List<String> header = csv.next();
        if (header == null) {
            throw new CollectorException("Fichier DATAtourisme vide : import des lieux interrompu.", null);
        }
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).replace("﻿", "").trim(), i);
        }
        List<String> missing = DataTourismeCollector.REQUIRED_COLUMNS.stream()
                .filter(column -> !columns.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new CollectorException("Format DATAtourisme inattendu, colonnes absentes : " + missing, null);
        }
        Set<String> seenUris = new HashSet<>();
        List<CollectedPlace> places = new ArrayList<>();
        Map<String, Integer> rejected = new TreeMap<>();
        int rows = 0;
        int malformed = 0;
        int placeRows = 0;
        List<String> values;
        while ((values = csv.next()) != null) {
            rows++;
            if (values.size() != header.size()) {
                malformed++;
                continue;
            }
            List<String> line = values;
            Map<String, String> row = new HashMap<>();
            columns.forEach((column, index) -> row.put(column, line.get(index)));
            Set<String> types = types(row.get("Categories_de_POI"));
            if (!types.contains(PLACE_TYPE) || types.stream().anyMatch(NOT_PLACE_TYPES::contains)) {
                continue;
            }
            placeRows++;
            CollectedPlace place = toCollectedPlace(row, types, rejected);
            if (place == null) {
                continue;
            }
            if (seenUris.add(place.externalId())) {
                places.add(place);
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
        if (placeRows == 0) {
            throw new CollectorException("Aucun lieu dans le fichier DATAtourisme (" + rows
                    + " lignes) : ressource ou format inattendu.", null);
        }
        LOGGER.info("DATAtourisme '{}' (lieux) : {} lignes, {} lieux, {} retenus, écartés : {}",
                sourceName, rows, placeRows, places.size(), rejected);
        return places;
    }

    private CollectedPlace toCollectedPlace(Map<String, String> row, Set<String> types, Map<String, Integer> rejected) {
        String uri = text(row.get("URI_ID_du_POI"));
        if (uri == null) {
            rejected.merge("sans URI", 1, Integer::sum);
            return null;
        }
        String[] postalCodeAndCity = firstObject(row.get("Code_postal_et_commune")).split("#", -1);
        String postalCode = postalCodeAndCity[0].trim();
        if (!departments.isEmpty() && departments.stream().noneMatch(postalCode::startsWith)) {
            rejected.merge("hors départements", 1, Integer::sum);
            return null;
        }
        Double latitude = number(row.get("Latitude"));
        Double longitude = number(row.get("Longitude"));
        if (latitude == null || longitude == null) {
            rejected.merge("sans coordonnées", 1, Integer::sum);
            return null;
        }
        String city = postalCodeAndCity.length > 1 ? text(postalCodeAndCity[1]) : null;
        return new CollectedPlace(
                uri, text(row.get("Nom_du_POI")), types, text(firstObject(row.get("Adresse_postale"))),
                text(postalCode), city, latitude, longitude, text(row.get("Description")),
                website(row.get("Contacts_du_POI")));
    }

    /**
     * Noms locaux des types de l'ontologie DATAtourisme ({@code https://…/core#Museum} → {@code Museum}). Les types
     * schema.org, présents en double dans le fichier, sont ignorés : la liste blanche ne porte que sur l'ontologie
     * DATAtourisme (avec eux, un « site culturel » sans sous-type ne serait plus reconnu).
     */
    static Set<String> types(String value) {
        Set<String> types = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return types;
        }
        for (String type : value.split("\\|")) {
            String trimmed = type.trim();
            if (trimmed.startsWith(CORE_NAMESPACE)) {
                types.add(trimmed.substring(CORE_NAMESPACE.length()));
            }
        }
        return types;
    }

    /** Site du lieu : première URL web d'un contact sans libellé, sinon première URL web. */
    static String website(String contacts) {
        if (contacts == null || contacts.isBlank()) {
            return null;
        }
        String firstAny = null;
        for (String contact : contacts.split("\\|")) {
            int separator = contact.indexOf('#');
            boolean labelled = separator > 0 && !contact.substring(0, separator).isBlank();
            String urls = separator >= 0 ? contact.substring(separator + 1) : contact;
            for (String url : urls.split("<>")) {
                String trimmed = url.trim();
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    if (!labelled) {
                        return trimmed;
                    }
                    if (firstAny == null) {
                        firstAny = trimmed;
                    }
                }
            }
        }
        return firstAny;
    }

    private static String firstObject(String value) {
        return value == null ? "" : value.split("\\|", -1)[0];
    }

    private static Double number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
