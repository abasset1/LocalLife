package com.locallife.backend.collector.infrastructure;

import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reprises bornées d'un appel OpenAgenda : un quota dépassé (HTTP 429), une erreur serveur (5xx), un réseau
 * indisponible ou une réponse illisible sont retentés jusqu'à {@code maxAttempts} fois, avec une attente
 * doublée à chaque essai (et au moins la durée {@code Retry-After} annoncée, plafonnée). Une autre erreur
 * (clé refusée, agenda introuvable) n'est jamais retentée : elle ne changerait pas.
 *
 * <p>L'échec final est une {@link CollectorException} expliquée par {@link OpenAgendaErrors#failure}, sans URL
 * ni clé. Les journaux ne reçoivent que le statut HTTP ou le type d'erreur, jamais le message d'une exception
 * réseau (qui porte l'URL, donc la clé).
 */
public final class OpenAgendaRetry {

    /** Attente réelle entre deux essais ; remplacée en test pour ne pas ralentir la suite. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAgendaRetry.class);

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofSeconds(2);

    /** Plafond d'un {@code Retry-After} : au-delà, l'import attendrait trop longtemps pour une seule page. */
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Sleeper sleeper;

    public OpenAgendaRetry(int maxAttempts, Duration initialBackoff, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts doit être au moins 1.");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.sleeper = sleeper;
    }

    public static OpenAgendaRetry withDefaults(int maxAttempts) {
        return new OpenAgendaRetry(maxAttempts, DEFAULT_INITIAL_BACKOFF, duration -> Thread.sleep(duration));
    }

    /** Sans attente : pour les tests. */
    static OpenAgendaRetry immediate(int maxAttempts) {
        return new OpenAgendaRetry(maxAttempts, Duration.ZERO, duration -> { });
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * @param context début du message d'erreur final, ex. « Échec de la collecte depuis OpenAgenda »
     * @throws CollectorException après le dernier essai, ou dès une erreur non retentable
     */
    public <T> T call(String context, Supplier<T> call) {
        Duration backoff = initialBackoff;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RestClientException exception) {
                if (attempt >= maxAttempts || !isRetryable(exception)) {
                    throw OpenAgendaErrors.failure(context, exception);
                }
                Duration wait = max(backoff, retryAfter(exception));
                LOGGER.warn("Appel OpenAgenda en échec ({}), essai {}/{} ; nouvel essai dans {} s",
                        describeForLog(exception), attempt, maxAttempts, wait.toSeconds());
                pause(wait);
                backoff = backoff.multipliedBy(2);
            }
        }
    }

    static boolean isRetryable(RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        // Réseau (ResourceAccessException) ou réponse illisible : peut être passager.
        return true;
    }

    private static Duration retryAfter(RestClientException exception) {
        if (exception instanceof RestClientResponseException response && response.getResponseHeaders() != null) {
            String value = response.getResponseHeaders().getFirst("Retry-After");
            if (value != null) {
                try {
                    long seconds = Long.parseLong(value.trim());
                    Duration announced = Duration.ofSeconds(Math.max(0, seconds));
                    return announced.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : announced;
                } catch (NumberFormatException ignored) {
                    // Date HTTP ou valeur inattendue : l'attente exponentielle suffit.
                }
            }
        }
        return Duration.ZERO;
    }

    private static Duration max(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static String describeForLog(RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        if (exception instanceof ResourceAccessException) {
            return "réseau";
        }
        return "réponse illisible";
    }

    private void pause(Duration wait) {
        if (wait.isZero()) {
            return;
        }
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CollectorException("Appel OpenAgenda interrompu pendant l'attente avant un nouvel essai.", null);
        }
    }
}
