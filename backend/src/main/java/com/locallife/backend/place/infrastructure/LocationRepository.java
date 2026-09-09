package com.locallife.backend.place.infrastructure;

import com.locallife.backend.place.domain.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Location (LL-11001). Même patron que
 * {@code ActivityRepository} : étend {@link Repository} (interface
 * marqueur) plutôt que {@code CrudRepository}, pour n'exposer que les
 * méthodes explicitement listées ici. Pas de recherche géographique
 * ({@code findWithinRadius}/{@code findWithinBounds} côté
 * {@code ActivityRepository}) à ce stade : hors périmètre de LL-11001
 * (« créer le modèle »), à ajouter par un futur ticket exploitant
 * réellement {@code geo_location} (ex. LL-11012, recherche sur les
 * occurrences).
 */
public interface LocationRepository extends Repository<Location, Long> {

    List<Location> findAll();

    Optional<Location> findById(Long id);

    Location save(Location location);
}
