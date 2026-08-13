package com.locallife.backend.activity.infrastructure;

import com.locallife.backend.activity.domain.Activity;
import java.time.LocalDate;
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
     *
     * Filtre optionnel par catégorie ajouté en LL-4004 : {@code categoriesCsv}
     * est une liste de catégories séparées par des virgules (ou {@code null}
     * pour ne pas filtrer), comparée à la colonne {@code category}
     * (chaîne libre, voir {@code ActivityService#findNearby} pour le détail
     * de cette décision). Utilise {@code string_to_array}/{@code ANY} côté
     * SQL plutôt qu'un binding de collection Java, pour rester sur le même
     * pattern « paramètre nullable unique » que {@code status} ci-dessus.
     *
     * Filtre optionnel par date ajouté en LL-4005 : {@code date} peut être
     * {@code null} (aucun filtrage), sinon une activité est retenue quand
     * {@code date} tombe dans sa période {@code [start_date, end_date]}
     * (bornes incluses, comparaison au jour près via {@code ::date}, donc
     * l'heure de {@code start_date}/{@code end_date} n'entre pas en jeu).
     * {@code end_date} peut être {@code NULL} en base (activités créées via
     * le formulaire de contribution, voir {@code ActivityService#createActivity})
     * : dans ce cas {@code COALESCE(end_date, start_date)} traite
     * l'activité comme ne durant qu'une seule journée, celle de
     * {@code start_date}.
     */
    @Query("""
            SELECT * FROM activity
            WHERE location IS NOT NULL
              AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
              AND (:status IS NULL OR status = :status)
              AND (:categoriesCsv IS NULL OR category = ANY(string_to_array(:categoriesCsv, ',')))
              AND (:date IS NULL OR :date BETWEEN start_date::date AND COALESCE(end_date, start_date)::date)
            ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)
            """)
    List<Activity> findWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("status") String status,
            @Param("categoriesCsv") String categoriesCsv,
            @Param("date") LocalDate date);

    /**
     * Recherche par zone cartographique PostGIS (LL-4007), conformément au
     * contrat défini en LL-4006
     * ({@code docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md}) :
     * activités dont la colonne {@code location} se trouve à l'intérieur
     * du rectangle défini par les coins sud-ouest
     * ({@code swLongitude}/{@code swLatitude}) et nord-est
     * ({@code neLongitude}/{@code neLatitude}). Utilise
     * {@code ST_MakeEnvelope} (SRID 4326) et l'opérateur {@code &&}
     * (comparaison de bounding box, exploitant l'index spatial existant)
     * plutôt que {@code ST_Within}/{@code ST_Contains} : suffisant ici
     * puisque la zone de recherche est elle-même un rectangle (pas de
     * polygone arbitraire à ce stade), et moins coûteux.
     *
     * Filtres optionnels {@code status}/{@code categoriesCsv}/{@code date}
     * : mêmes sémantiques que {@link #findWithinRadius}, voir les
     * javadocs correspondantes ci-dessus (LL-4003/LL-4004/LL-4005).
     *
     * Pas de point de référence unique pour une distance : résultats
     * triés par {@code id} croissant (décision du contrat LL-4006).
     */
    @Query("""
            SELECT * FROM activity
            WHERE location IS NOT NULL
              AND location && ST_MakeEnvelope(:swLongitude, :swLatitude, :neLongitude, :neLatitude, 4326)::geography
              AND (:status IS NULL OR status = :status)
              AND (:categoriesCsv IS NULL OR category = ANY(string_to_array(:categoriesCsv, ',')))
              AND (:date IS NULL OR :date BETWEEN start_date::date AND COALESCE(end_date, start_date)::date)
            ORDER BY id
            """)
    List<Activity> findWithinBounds(
            @Param("swLatitude") double swLatitude,
            @Param("swLongitude") double swLongitude,
            @Param("neLatitude") double neLatitude,
            @Param("neLongitude") double neLongitude,
            @Param("status") String status,
            @Param("categoriesCsv") String categoriesCsv,
            @Param("date") LocalDate date);

}
