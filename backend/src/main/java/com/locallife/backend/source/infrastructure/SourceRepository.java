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

}
