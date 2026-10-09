package com.locallife.backend.push.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envoi de notifications push via le service Expo Push
 * (https://docs.expo.dev/push-notifications/sending-notifications/) : un
 * seul point d'envoi pour Android (FCM) et iOS (APNs), les jetons
 * {@code ExponentPushToken[...]} étant obtenus par l'application mobile.
 *
 * {@code EXPO_ACCESS_TOKEN} n'est nécessaire que si la sécurité renforcée
 * des notifications push est activée sur le projet Expo.
 */
@Service
public class ExpoPushService {

    /** Limite d'Expo : 100 notifications par requête. */
    static final int MAX_MESSAGES_PER_REQUEST = 100;

    private final RestClient restClient;

    @Autowired
    public ExpoPushService(
            @Value("${app.push.expo-url:https://exp.host/--/api/v2/push/send}") String expoUrl,
            @Value("${app.push.expo-access-token:}") String accessToken) {
        this(RestClient.builder(), expoUrl, accessToken);
    }

    /** Visible pour les tests : {@link RestClient.Builder} lié à un {@code MockRestServiceServer}. */
    ExpoPushService(RestClient.Builder builder, String expoUrl, String accessToken) {
        RestClient.Builder configured = builder.baseUrl(expoUrl);
        if (accessToken != null && !accessToken.isBlank()) {
            configured = configured.defaultHeader("Authorization", "Bearer " + accessToken);
        }
        this.restClient = configured.build();
    }

    /** Format des jetons émis par {@code expo-notifications}. */
    public static boolean isExpoPushToken(String token) {
        return token != null && token.matches("^Expo(nent)?PushToken\\[[A-Za-z0-9_-]+]$");
    }

    /**
     * Envoie la même notification à chaque jeton.
     *
     * @param data données transmises à l'application (ex. écran à ouvrir au toucher)
     * @return les jetons refusés par Expo comme n'étant plus enregistrés
     *         ({@code DeviceNotRegistered}), à supprimer
     * @throws PushUnavailableException si Expo est injoignable ou refuse la requête
     */
    public List<String> send(List<String> tokens, String title, String body, Map<String, Object> data) {
        List<String> unregistered = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += MAX_MESSAGES_PER_REQUEST) {
            List<String> batch = tokens.subList(start, Math.min(tokens.size(), start + MAX_MESSAGES_PER_REQUEST));
            List<Map<String, Object>> messages = batch.stream()
                    .map(token -> Map.<String, Object>of(
                            "to", token, "title", title, "body", body, "data", data, "sound", "default"))
                    .toList();
            ExpoResponse response;
            try {
                response = restClient.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(messages)
                        .retrieve()
                        .body(new ParameterizedTypeReference<ExpoResponse>() { });
            } catch (RestClientException exception) {
                throw new PushUnavailableException("Le service de notifications est indisponible.", exception);
            }
            List<ExpoTicket> tickets = response == null || response.data() == null ? List.of() : response.data();
            // Les tickets sont renvoyés dans l'ordre des messages envoyés.
            for (int index = 0; index < tickets.size() && index < batch.size(); index++) {
                ExpoTicket ticket = tickets.get(index);
                if ("error".equals(ticket.status()) && ticket.details() != null
                        && "DeviceNotRegistered".equals(ticket.details().error())) {
                    unregistered.add(batch.get(index));
                }
            }
        }
        return unregistered;
    }

    private record ExpoResponse(List<ExpoTicket> data) {
    }

    private record ExpoTicket(String status, String message, ExpoTicketDetails details) {
    }

    private record ExpoTicketDetails(String error) {
    }

}
