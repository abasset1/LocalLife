package com.locallife.backend.collector.application;

/**
 * Levée par {@link ImportService#importAll()} quand un import est déjà en cours dans cette instance :
 * deux imports simultanés traiteraient les mêmes sources en parallèle (constaté le 08/10/2026, un
 * import manuel chevauché par l'import planifié de l'heure pile).
 */
public class ImportAlreadyRunningException extends RuntimeException {

    public ImportAlreadyRunningException() {
        super("Un import est déjà en cours.");
    }

}
