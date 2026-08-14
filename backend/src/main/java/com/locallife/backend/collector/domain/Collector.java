package com.locallife.backend.collector.domain;

import java.util.List;

/**
 * Interface commune aux collecteurs (LL-5003, documentée dans
 * {@code COLLECTOR_CONTRACT.md}). Créée en code ici, à l'occasion de
 * LL-5006 : c'était le premier ticket à en avoir réellement besoin (voir
 * la note dans {@code COLLECTOR_CONTRACT.md}).
 */
public interface Collector {

    /**
     * Nom de la {@code Source} (voir {@code SOURCE_CONTRACT.md}) à
     * laquelle appartiennent les données collectées.
     */
    String getSourceName();

    /**
     * Récupère les données depuis la source externe, sous forme de
     * données brutes normalisables. Ne fait aucune normalisation, aucune
     * validation, aucune écriture en base.
     */
    List<CollectedActivity> collect();

}
