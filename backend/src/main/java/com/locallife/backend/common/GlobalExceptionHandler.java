package com.locallife.backend.common;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Gestion globale des exceptions non gérées : transforme toute exception
 * en une réponse JSON standardisée (voir {@link ErrorResponse}).
 *
 * Volontairement générique, sans logique métier.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");

        // Niveau ERROR : exception non anticipée entraînant une réponse 500.
        // On ne journalise volontairement ni les en-têtes ni le corps de la
        // requête (susceptibles de contenir un mot de passe ou un JWT) :
        // seuls le type d'exception, son message et le chemin appelé sont tracés.
        LOGGER.error("Erreur serveur non gérée sur {} : {}", path, exception.toString(), exception);

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                exception.getMessage(),
                path);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

}
