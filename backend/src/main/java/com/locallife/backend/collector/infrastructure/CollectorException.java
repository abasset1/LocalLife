package com.locallife.backend.collector.infrastructure;

/**
 * Signale un échec de collecte (configuration manquante, appel réseau en
 * échec, réponse invalide). Non vérifiée, comme
 * {@code GeocodingUnavailableException} — le type d'exception n'était pas
 * imposé par {@code COLLECTOR_CONTRACT.md} (LL-5003), fixé ici pour LL-5006.
 */
public class CollectorException extends RuntimeException {

    public CollectorException(String message, Throwable cause) {
        super(message, cause);
    }

}
