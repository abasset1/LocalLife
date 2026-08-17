package com.locallife.backend.foodtruck.infrastructure;

import com.locallife.backend.foodtruck.domain.FoodTruck;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repository FoodTruck — opérations minimales (LL-6009) : création et
 * consultation par statut. Étend {@link Repository} (interface marqueur,
 * sans méthode) plutôt que {@code CrudRepository}, comme
 * {@code SourceRepository}/{@code CategoryRepository} : seules les
 * méthodes explicitement listées ici sont disponibles.
 */
public interface FoodTruckRepository extends Repository<FoodTruck, Long> {

    FoodTruck save(FoodTruck foodTruck);

    /**
     * Utilisé par {@code FoodTruckService#findAllPublished} : seuls les
     * food trucks {@code PUBLISHED} sont retournés par l'endpoint public
     * (LL-6009), même convention qu'{@code ActivityRepository} depuis
     * LL-6004 pour les activités.
     */
    List<FoodTruck> findByStatus(String status);

}
