package com.locallife.backend.pro.application;

/**
 * Refus d'une action de l'espace professionnel ou de sa modération (ADR-0012, ADR-0013), avec sa nature :
 * le contrôleur en déduit le code HTTP.
 */
public class ProActionException extends RuntimeException {

    /** Nature du refus. */
    public enum Reason {
        /** Requête invalide (400). */
        INVALID,
        /** Compte sans droit pour cette action (403). */
        FORBIDDEN,
        /** Ressource inexistante ou invisible pour ce compte (404). */
        NOT_FOUND,
        /** Action contraire à l'état courant (409). */
        CONFLICT,
        /** Service externe indisponible (503). */
        UNAVAILABLE
    }

    private final Reason reason;

    public ProActionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static ProActionException invalid(String message) {
        return new ProActionException(Reason.INVALID, message);
    }

    public static ProActionException forbidden(String message) {
        return new ProActionException(Reason.FORBIDDEN, message);
    }

    public static ProActionException notFound(String message) {
        return new ProActionException(Reason.NOT_FOUND, message);
    }

    public static ProActionException conflict(String message) {
        return new ProActionException(Reason.CONFLICT, message);
    }

    public static ProActionException unavailable(String message) {
        return new ProActionException(Reason.UNAVAILABLE, message);
    }
}
