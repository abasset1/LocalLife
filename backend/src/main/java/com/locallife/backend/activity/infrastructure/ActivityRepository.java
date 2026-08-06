package com.locallife.backend.activity.infrastructure;

import com.locallife.backend.activity.domain.Activity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Activity — uniquement des opérations de lecture.
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository} : aucune méthode d'écriture n'est disponible, même
 * par erreur.
 */
public interface ActivityRepository extends Repository<Activity, Long> {

    List<Activity> findAll();

    Optional<Activity> findById(Long id);

}
