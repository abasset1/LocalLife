package com.locallife.backend.activity.infrastructure;

import com.locallife.backend.activity.domain.Activity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

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

    /**
     * Recherche géographique PostGIS (LL-4002) : activités dont la colonne
     * {@code location} (alimentée automatiquement depuis latitude/longitude
     * par un trigger, voir {@code V7__add_postgis_location_to_activity.sql})
     * se trouve à moins de {@code radiusMeters} mètres du point donné.
     * Distance calculée côté base ({@code ST_DWithin} sur type {@code
     * geography}, donc en mètres), résultats triés par distance croissante.
     * La conversion km → mètres (contrat LL-4001 : {@code radius} exprimé
     * en km côté API) est à la charge de l'appelant, voir
     * {@code ActivityService#findNearby}.
     *
     * Filtre optionnel par statut ajouté en LL-4003 : {@code status} peut
     * être {@code null}, auquel cas aucun filtrage n'est appliqué (même
     * comportement que {@code findAll()}, qui ne filtre pas non plus).
     */
    @Query("""
            SELECT * FROM activity
            WHERE location IS NOT NULL
              AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
              AND (:status IS NULL OR status = :status)
            ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)
            """)
    List<Activity> findWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("status") String status);

}
