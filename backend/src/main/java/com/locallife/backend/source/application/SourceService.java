package com.locallife.backend.source.application;

import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Source — minimal, simple délégation vers le repository (LL-5002).
 * Aucune logique de collecte : ce service ne fait qu'enregistrer et
 * consulter des sources, comme {@code CategoryService}.
 */
@Service
public class SourceService {

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    /**
     * Crée une source active. {@code lastSyncAt} démarre à {@code null}
     * (aucun import n'a encore eu lieu), conformément à
     * {@code SOURCE_CONTRACT.md}.
     */
    public Source createSource(String name, String type, String url) {
        Source source = new Source(null, name, type, url, "ACTIVE", null);
        return sourceRepository.save(source);
    }

    public List<Source> getAllSources() {
        return sourceRepository.findAll();
    }

    public Optional<Source> getSourceById(Long id) {
        return sourceRepository.findById(id);
    }

    /**
     * Recherche la source réservée par type (LL-5008) — utilisée pour
     * retrouver la source {@code MANUAL} sans dépendre de son libellé
     * exact (voir {@code SourceRepository#findByType}).
     */
    public Optional<Source> findByType(String type) {
        return sourceRepository.findByType(type);
    }

    /**
     * Retrouve une source par nom, ou la crée si elle n'existe pas encore
     * (LL-5008) — c'est le rapprochement « recherche par nom, création si
     * absente » explicitement différé à ce ticket par
     * {@code SOURCE_CONTRACT.md} et {@code COLLECTOR_CONTRACT.md}.
     * Utilisée par le pipeline d'import pour retrouver la {@code Source}
     * correspondant au {@code getSourceName()} d'un {@code Collector},
     * quitte à en créer une la première fois qu'un collecteur donné est
     * exécuté.
     */
    public Source findOrCreateByName(String name, String type, String url) {
        return sourceRepository.findByName(name).orElseGet(() -> createSource(name, type, url));
    }

}
