package com.locallife.backend.source.infrastructure;

import com.locallife.backend.source.domain.Source;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Source — opérations minimales (LL-5002) : création et
 * consultation. Aucun mécanisme de collecte ni de mise à jour n'est
 * nécessaire à ce stade (voir {@code SOURCE_CONTRACT.md}).
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository} : seules les méthodes explicitement listées ici
 * sont disponibles, comme {@code CategoryRepository} et
 * {@code UserRepository}.
 */
public interface SourceRepository extends Repository<Source, Long> {

    Source save(Source source);

    List<Source> findAll();

    Optional<Source> findById(Long id);

    /**
     * Recherche par nom (LL-5008) : sert à rapprocher un {@code Collector}
     * (identifié par {@code getSourceName()}, voir
     * {@code COLLECTOR_CONTRACT.md}) de la ligne {@code Source}
     * correspondante — création si absente, voir
     * {@code SourceService#findOrCreateByName}.
     */
    Optional<Source> findByName(String name);

    /**
     * Recherche par type (LL-5008) : sert à retrouver la source réservée
     * {@code MANUAL} (une seule ligne, insérée par la migration
     * {@code V8__create_source_table.sql}) sans dépendre de son libellé
     * exact, utilisé par {@code ActivityService#createActivity}.
     */
    Optional<Source> findByType(String type);

}
