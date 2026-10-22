package com.locallife.backend.auth.application;

/**
 * Action sensible d'un administrateur sans confirmation valide de son mot de passe (en-tête
 * {@value ReauthenticationService#HEADER}) : 428, les interfaces redemandent le mot de passe.
 */
public class ReauthenticationRequiredException extends RuntimeException {

    public ReauthenticationRequiredException(String message) {
        super(message);
    }
}
