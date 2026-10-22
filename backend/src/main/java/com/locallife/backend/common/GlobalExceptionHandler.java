package com.locallife.backend.common;

import com.locallife.backend.auth.application.ReauthenticationRequiredException;
import com.locallife.backend.auth.application.TooManyLoginAttemptsException;
import com.locallife.backend.subscription.application.PlanLimitException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Gestion globale des exceptions non gérées : transforme toute exception
 * en une réponse JSON standardisée (voir {@link ErrorResponse}).
 *
 * Volontairement générique, sans logique métier.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Route inexistante : Spring MVC, faute de contrôleur, cherche une
     * ressource statique et lève {@link NoResourceFoundException}. C'est une
     * erreur de l'appelant (ou un backend pas à jour par rapport au
     * frontend), pas une panne serveur : 404 et simple avertissement, au lieu
     * du 500 « erreur serveur non gérée » du gestionnaire générique.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        LOGGER.warn("Route introuvable : {} {}", exception.getHttpMethod(), path);

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "Route introuvable : " + exception.getHttpMethod() + " " + path,
                path);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /** Limite de l'offre atteinte (favoris, alertes, contributions Premium, {@code PlanLimits}) : 403 explicite. */
    @ExceptionHandler(PlanLimitException.class)
    public ResponseEntity<ErrorResponse> handlePlanLimit(PlanLimitException exception, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                exception.getMessage(),
                path);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Action sensible sans confirmation valide du mot de passe ({@code ReauthenticationService}) : 428. */
    @ExceptionHandler(ReauthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleReauthentication(ReauthenticationRequiredException exception,
            WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.PRECONDITION_REQUIRED.value(),
                HttpStatus.PRECONDITION_REQUIRED.getReasonPhrase(), exception.getMessage(), path);
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(body);
    }

    /** Trop de tentatives de connexion échouées ({@code LoginThrottle}) : 429. */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyLogins(TooManyLoginAttemptsException exception,
            WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), exception.getMessage(), path);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

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
