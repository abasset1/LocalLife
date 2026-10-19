package com.locallife.backend.foodtruck.infrastructure;

import com.locallife.backend.foodtruck.domain.FoodTruck;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository FoodTruck — opérations minimales (LL-6009) : création et
 * consultation par id. Les présences (ADR-0013) ont leur propre dépôt,
 * {@code FoodTruckPresenceRepository}. Étend {@link Repository} (interface marqueur,
 * sans méthode) plutôt que {@code CrudRepository}, comme
 * {@code SourceRepository}/{@code CategoryRepository} : seules les
 * méthodes explicitement listées ici sont disponibles.
 */
public interface FoodTruckRepository extends Repository<FoodTruck, Long> {

    FoodTruck save(FoodTruck foodTruck);

    Optional<FoodTruck> findById(Long id);

}
