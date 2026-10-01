package com.locallife.backend.activitytype.infrastructure;

import com.locallife.backend.activitytype.domain.ActivityType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ActivityType — uniquement des opérations de lecture (CAT-002 :
 * les 38 types V1 sont créés une fois pour toutes par migration, pas par
 * une API d'écriture — même convention que {@code CategoryRepository}).
 *
 * Étend {@link Repository} (interface marqueur, sans méthode) plutôt que
 * {@code CrudRepository}, comme les autres repositories de ce projet.
 */
public interface ActivityTypeRepository extends Repository<ActivityType, Long> {

    List<ActivityType> findAll();

    Optional<ActivityType> findById(Long id);

    /**
     * Recherche par code métier — même rôle que
     * {@code CategoryRepository#findByCode}, pour les futurs tickets qui
     * devront résoudre un code de type reçu depuis l'API ou l'import.
     */
    Optional<ActivityType> findByCode(String code);

}
