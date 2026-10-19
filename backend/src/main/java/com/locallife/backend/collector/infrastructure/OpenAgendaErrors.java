package com.locallife.backend.collector.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Appels à l'API OpenAgenda : délais d'attente et explication lisible d'un échec.
 *
 * <p>Sans délai, un appel qui ne répond jamais bloquait l'import en cours, et avec lui tous les imports planifiés
 * suivants (un seul import à la fois). Un échec était réduit à « Échec de la collecte depuis OpenAgenda. » : la
 * cause (clé refusée, quota, réseau) n'apparaissait que dans les journaux, quand elle y apparaissait.
 *
 * <p>La clé API passe dans l'URL ({@code ?key=}) : aucun message produit ici ne contient l'URL, et la cause d'un
 * échec réseau (dont le message la contient) n'est pas conservée.
 */
public final class OpenAgendaErrors {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_API_MESSAGE_LENGTH = 200;

    private OpenAgendaErrors() {
    }

    /** {@link RestClient.Builder} avec délais de connexion et de lecture. */
    public static RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    /** Échec d'un appel OpenAgenda, expliqué : {@code context} puis la cause, sans URL ni clé. */
    public static CollectorException failure(String context, RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            return new CollectorException(context + " : " + describe(response.getStatusCode().value(),
                    response.getResponseBodyAsString()), exception);
        }
        if (exception instanceof ResourceAccessException) {
            return new CollectorException(context + " : OpenAgenda injoignable (délai dépassé ou réseau "
                    + "indisponible). Réessaie plus tard.", null);
        }
        return new CollectorException(context + " : réponse d'OpenAgenda illisible ("
                + exception.getClass().getSimpleName() + ").", null);
    }

    /** Explication d'une réponse d'erreur, avec le message renvoyé par OpenAgenda s'il y en a un. */
    static String describe(int status, String body) {
        String apiMessage = apiMessage(body);
        String suffix = apiMessage == null ? "" : " (OpenAgenda : « " + apiMessage + " »)";
        return switch (status) {
            case 401, 403 -> "clé API refusée par OpenAgenda (HTTP " + status + ")" + suffix
                    + ". Vérifie OPENAGENDA_API_KEY : clé révoquée, régénérée ou mal recopiée ?";
            case 404 -> "ressource introuvable chez OpenAgenda (HTTP 404)" + suffix
                    + ". L'identifiant d'agenda est-il toujours valable ?";
            case 429 -> "trop de requêtes, OpenAgenda limite les appels (HTTP 429)" + suffix + ". Réessaie plus tard.";
            default -> status >= 500
                    ? "OpenAgenda est en erreur (HTTP " + status + ")" + suffix + ". Réessaie plus tard."
                    : "requête refusée par OpenAgenda (HTTP " + status + ")" + suffix + ".";
        };
    }

    private static String apiMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode message = OBJECT_MAPPER.readTree(body).get("message");
            if (message == null || !message.isTextual() || message.asText().isBlank()) {
                return null;
            }
            String text = message.asText().trim();
            return text.length() > MAX_API_MESSAGE_LENGTH ? text.substring(0, MAX_API_MESSAGE_LENGTH) + "…" : text;
        } catch (Exception exception) {
            return null;
        }
    }
}
