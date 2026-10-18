package com.locallife.backend.place.application;

/**
 * Refus d'une action de l'espace professionnel ou de la modération des revendications (ADR-0012), avec sa nature :
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

    static ProActionException invalid(String message) {
        return new ProActionException(Reason.INVALID, message);
    }

    static ProActionException forbidden(String message) {
        return new ProActionException(Reason.FORBIDDEN, message);
    }

    static ProActionException notFound(String message) {
        return new ProActionException(Reason.NOT_FOUND, message);
    }

    static ProActionException conflict(String message) {
        return new ProActionException(Reason.CONFLICT, message);
    }

    static ProActionException unavailable(String message) {
        return new ProActionException(Reason.UNAVAILABLE, message);
    }
}
