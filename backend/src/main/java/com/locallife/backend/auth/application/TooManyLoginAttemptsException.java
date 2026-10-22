package com.locallife.backend.auth.application;

/** Trop de tentatives de connexion échouées pour une adresse ({@link LoginThrottle}) : 429. */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Trop de tentatives de connexion : réessaie dans " + LoginThrottle.WINDOW_MINUTES + " minutes, ou "
                + "réinitialise ton mot de passe.");
    }
}
