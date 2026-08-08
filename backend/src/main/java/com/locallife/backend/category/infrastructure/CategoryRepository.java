package com.locallife.backend.category.infrastructure;

import com.locallife.backend.category.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Category — uniquement des opérations de lecture.
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository} : aucune méthode d'écriture n'est disponible, même
 * par erreur.
 */
public interface CategoryRepository extends Repository<Category, Long> {

    List<Category> findAll();

    Optional<Category> findById(Long id);

}
