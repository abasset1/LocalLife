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

    /**
     * Liste des activités avec filtre optionnel par ville et tri optionnel
     * (LL-10006), conformément au contrat défini dans
     * {@code docs/02_Architecture/LOCATION_CONTRACT.md} (section « Filtre
     * `city` et tri `sort` »). Utilisé uniquement par
     * {@code GET /api/v1/activities} — {@code findWithinRadius}/
     * {@code findWithinBounds} ci-dessous ne sont pas concernées par ce
     * ticket (leurs propres contrats géographiques restent inchangés).
     *
     * {@code city} : {@code null} pour ne pas filtrer, sinon comparaison
     * exacte insensible à la casse ({@code LOWER()} des deux côtés) — voir
     * {@code ActivityService#findAll(String, String)} pour la validation
     * en amont. Cast explicite ({@code :city::text IS NULL}) par prudence,
     * même raison que {@code :date::date IS NULL} sur
     * {@link #findWithinRadius} (déjà rencontré sur ce projet :
     * PostgreSQL ne peut pas toujours déterminer le type d'un paramètre à
     * partir d'un simple {@code IS NULL} sans contexte).
     *
     * {@code primarySort}/{@code secondarySort} : chacun {@code null},
     * {@code "city"} ou {@code "date"} (jamais une autre valeur — validées
     * en amont par le service, liste fermée du contrat LL-10006), désigne
     * respectivement la première et la seconde clé de tri demandées via
     * le paramètre {@code sort} (ex. {@code sort=city,date} →
     * {@code primarySort="city"}, {@code secondarySort="date"}). Une seule
     * requête couvre toutes les combinaisons possibles (aucune, une seule
     * clé, ou les deux dans n'importe quel ordre) via des expressions
     * {@code CASE WHEN} : quand une clé ne correspond pas au paramètre
     * fourni, la branche {@code CASE} vaut {@code NULL} et n'a donc aucun
     * effet sur le tri à cette position. {@code id} croissant est toujours
     * ajouté en dernier critère pour un résultat déterministe même à
     * égalité parfaite sur {@code city}/{@code date} (voir la décision
     * correspondante dans {@code LOCATION_CONTRACT.md}).
     *
     * Volontairement distincte de {@link #findAll()} plutôt que de la
     * remplacer : {@code findAll()} reste appelée telle quelle par
     * {@code ActivityService#findAll(String, String)} quand ni
     * {@code city} ni {@code sort} ne sont fournis, pour ne strictement
     * rien changer au comportement observable historique de
     * {@code GET /api/v1/activities} dans ce cas (aucun {@code ORDER BY}
     * ajouté), conformément au critère d'acceptation « les filtres
     * existants restent fonctionnels ».
     */
    @Query("""
            SELECT * FROM activity
            WHERE (:city::text IS NULL OR LOWER(city) = LOWER(:city::text))
            ORDER BY
              CASE WHEN :primarySort = 'city' THEN city END ASC,
              CASE WHEN :primarySort = 'date' THEN start_date END ASC,
              CASE WHEN :secondarySort = 'city' THEN city END ASC,
              CASE WHEN :secondarySort = 'date' THEN start_date END ASC,
              id ASC
            """)
    List<Activity> findAllFiltered(
            @Param("city") String city,
            @Param("primarySort") String primarySort,
            @Param("secondarySort") String secondarySort);

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
     * {@code null}; dans ce cas, seules les activités encore en cours ou à
     * venir sont retenues. Lorsque {@code date} est fourni, une activité est
     * retenue quand {@code date} tombe dans sa période
     * {@code [start_date, end_date]}
     * (bornes incluses, comparaison au jour près via {@code ::date}, donc
     * l'heure de {@code start_date}/{@code end_date} n'entre pas en jeu).
     * {@code end_date} peut être {@code NULL} en base (activités créées via
     * le formulaire de contribution, voir {@code ActivityService#createActivity})
     * : dans ce cas {@code COALESCE(end_date, start_date)} traite
     * l'activité comme ne durant qu'une seule journée, celle de
     * {@code start_date}.
     *
     * ⚠️ Correctif (post-LL-4007, signalé par Alex via {@code mvn verify}) :
     * {@code :date} doit être casté explicitement (
     * {@code :date::date}), y compris dans le test {@code IS NULL} —
     * sinon PostgreSQL ne peut pas déterminer le type du paramètre à
     * partir d'un simple {@code ? IS NULL} sans contexte de type, ce qui
     * lève {@code could not determine data type of parameter}
     * ({@code BadSqlGrammarException} côté Spring) dès qu'une requête est
     * exécutée avec {@code date == null}. Bug introduit en LL-4005,
     * non détecté avant faute d'accès à une vraie base PostgreSQL en
     * sandbox de développement. Même raison pour {@code :dateTo::date}
     * ci-dessous.
     *
     * Filtre par période ajouté en LL-11003 : {@code dateTo} peut être
     * {@code null} (comportement LL-4005 inchangé : correspondance sur la
     * seule date {@code date}) ; lorsque {@code dateTo} est fourni (toujours
     * accompagné de {@code date}, validé en amont par {@code
     * ActivityService#validateDateRange}), une activité est retenue dès
     * que sa période {@code [start_date, end_date]} chevauche {@code
     * [date, dateTo]} — {@code start_date::date <= dateTo::date AND
     * COALESCE(end_date, start_date)::date >= date::date} — plutôt que de
     * ne correspondre qu'à une unique journée.
     */
    @Query("""
            SELECT * FROM activity
            WHERE location IS NOT NULL
              AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
              AND (:status IS NULL OR status = :status)
              AND (:categoriesCsv IS NULL OR category = ANY(string_to_array(:categoriesCsv, ',')))
              AND ((:date::date IS NULL
                    AND (start_date::date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days' OR
                         COALESCE(end_date, start_date)::date BETWEEN CURRENT_DATE
                         AND CURRENT_DATE + INTERVAL '7 days'))
                   OR (:date::date IS NOT NULL AND :dateTo::date IS NULL
                       AND :date::date BETWEEN start_date::date AND COALESCE(end_date, start_date)::date)
                   OR (:date::date IS NOT NULL AND :dateTo::date IS NOT NULL
                       AND start_date::date <= :dateTo::date
                       AND COALESCE(end_date, start_date)::date >= :date::date))
            ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)
            """)
    List<Activity> findWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("status") String status,
            @Param("categoriesCsv") String categoriesCsv,
            @Param("date") LocalDate date,
            @Param("dateTo") LocalDate dateTo);

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
     * Filtres optionnels {@code status}/{@code categoriesCsv}/{@code date}/
     * {@code dateTo} : mêmes sémantiques que {@link #findWithinRadius},
     * voir les javadocs correspondantes ci-dessus (LL-4003/LL-4004/
     * LL-4005/LL-11003), y compris le correctif {@code :date::date}/
     * {@code :dateTo::date} contre {@code BadSqlGrammarException} sur
     * paramètre {@code null}.
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
              AND ((:date::date IS NULL
                    AND (start_date::date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days' OR
                         COALESCE(end_date, start_date)::date BETWEEN CURRENT_DATE
                         AND CURRENT_DATE + INTERVAL '7 days'))
                   OR (:date::date IS NOT NULL AND :dateTo::date IS NULL
                       AND :date::date BETWEEN start_date::date AND COALESCE(end_date, start_date)::date)
                   OR (:date::date IS NOT NULL AND :dateTo::date IS NOT NULL
                       AND start_date::date <= :dateTo::date
                       AND COALESCE(end_date, start_date)::date >= :date::date))
            ORDER BY id
            """)
    List<Activity> findWithinBounds(
            @Param("swLatitude") double swLatitude,
            @Param("swLongitude") double swLongitude,
            @Param("neLatitude") double neLatitude,
            @Param("neLongitude") double neLongitude,
            @Param("status") String status,
            @Param("categoriesCsv") String categoriesCsv,
            @Param("date") LocalDate date,
            @Param("dateTo") LocalDate dateTo);

    /**
     * Recherche par source et clé de déduplication (LL-5008) : retrouve
     * une activité déjà importée pour décider si le pipeline doit la
     * mettre à jour (trouvée) ou en créer une nouvelle (absente). Voir
     * {@code DeduplicationService} (LL-5007) pour le calcul de
     * {@code importKey} et {@code ImportService} pour l'utilisation.
     */
    Optional<Activity> findBySourceIdAndImportKey(Long sourceId, String importKey);

    /**
     * Toutes les activités d'une source donnée (LL-5008), y compris déjà
     * archivées — utilisé par {@code ImportService} pour repérer celles
     * qui n'apparaissent plus dans la dernière collecte (stratégie de
     * suppression documentée dans sa javadoc) et pour éviter de retoucher
     * celles déjà {@code ARCHIVED}.
     */
    List<Activity> findBySourceId(Long sourceId);

    /**
     * Toutes les activités correspondant exactement à un statut donné
     * (LL-6005) : utilisé par {@code ActivityService#findByStatus} pour
     * la consultation administrative par statut (ex. file de modération
     * {@code PENDING}). Requête dérivée du nom de la méthode (comme
     * {@link #findBySourceId} ci-dessus) — pas de {@code @Query}
     * nécessaire, {@code status} étant une simple colonne de
     * {@code activity} depuis LL-6003 (contrainte {@code CHECK}, voir
     * {@code V11__enforce_activity_status.sql}). Aucun tri particulier
     * (pas de critère dans le ticket) ; aucun filtre géographique,
     * contrairement à {@link #findWithinRadius}/{@link #findWithinBounds}
     * — voir la javadoc de {@code ActivityService#findByStatus} pour la
     * distinction avec ces deux méthodes.
     */
    List<Activity> findByStatus(String status);

}
