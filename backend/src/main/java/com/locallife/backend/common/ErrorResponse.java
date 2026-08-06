package com.locallife.backend.common;

import java.time.Instant;

/**
 * Format JSON standardisé pour toute réponse d'erreur renvoyée par l'API.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
