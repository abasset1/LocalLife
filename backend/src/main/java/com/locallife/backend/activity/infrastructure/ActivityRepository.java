package com.locallife.backend.activity.infrastructure;

import com.locallife.backend.activity.domain.Activity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Activity.
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository} : seules les méthodes explicitement listées ici
 * sont disponibles. {@code save} a été ajouté hors périmètre initial du
 * Sprint 2, à la demande d'Alex, pour débloquer LL-2012 (formulaire de
 * contribution).
 */
public interface ActivityRepository extends Repository<Activity, Long> {

    List<Activity> findAll();

    Optional<Activity> findById(Long id);

    Activity save(Activity activity);

}
