package com.locallife.backend.collector.application;

/** Un lot de découverte des agendas est déjà en cours : 409 plutôt que deux parcours concurrents. */
public class DiscoveryAlreadyRunningException extends RuntimeException {

    public DiscoveryAlreadyRunningException() {
        super("Une découverte des agendas est déjà en cours. Réessaie à la fin du lot en cours.");
    }
}
