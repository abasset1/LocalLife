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
 *
 * <b>LL-11012 (« recherche sur les occurrences »)</b> : {@link
 * #findAllFiltered}/{@link #findWithinRadius}/{@link #findWithinBounds}
 * filtrent désormais au niveau du modèle événementiel riche du Sprint 11
 * ({@code schedule}/{@code occurrence}/{@code location}) plutôt qu'au
 * seul niveau des colonnes plates d'{@code activity} — voir
 * {@code docs/ADR-0006-recherche-occurrences.md} pour la décision de
 * conception détaillée (bloc SQL {@code EXISTS} occurrence-aware,
 * repli sur les colonnes d'{@code activity} pour les activités sans
 * {@code schedule} — contributions manuelles, LL-2012/LL-3012).
 */
public interface ActivityRepository extends Repository<Activity, Long> {

    List<Activity> findAll();

    /**
     * Liste des activités avec filtres optionnels par ville et par date, et
     * tri optionnel (LL-10006, filtre {@code date} ajouté en LL-11012),
     * conformément au contrat défini dans
     * {@code docs/02_Architecture/LOCATION_CONTRACT.md} (section « Filtre
     * `city` et tri `sort` ») et à {@code docs/ADR-0006-recherche-occurrences.md}
     * pour {@code date}. Activités {@code PUBLISHED} uniquement (endpoint
     * public, audit #93). Utilisée uniquement par
     * {@code GET /api/v1/activities} — {@code findWithinRadius}/
     * {@code findWithinBounds} ci-dessous ont leurs propres contrats
     * géographiques, adaptés séparément par ce même ticket.
     *
     * {@code city}/{@code date} : {@code null} chacun pour ne pas filtrer
     * (indépendamment l'un de l'autre, comme {@code category}/{@code date}
     * sur {@link #findWithinRadius}). Une activité correspond si :
     * <ul>
     *   <li>elle a au moins un {@code schedule} (LL-11002) : le filtre est
     *       évalué sur ses occurrences non annulées ({@code status <>
     *       'CANCELLED'}, « exceptions prises en compte »), en résolvant le
     *       lieu effectif de chacune ({@code COALESCE(occurrence.location_id,
     *       schedule.location_id)}, « changement exceptionnel de lieu ») —
     *       couvre nativement activité ponctuelle/récurrente, plusieurs
     *       schedules, plusieurs villes (LL-11012, « le système doit
     *       gérer ») ;</li>
     *   <li>sinon (aucun {@code schedule}, ex. contribution manuelle
     *       LL-2012/LL-3012, dont le formulaire ne crée encore ni
     *       {@code Location} ni {@code Schedule}) : repli sur les colonnes
     *       plates historiques {@code activity.city}/{@code
     *       activity.start_date}/{@code activity.end_date} — garantit
     *       qu'aucune contribution manuelle existante ne disparaît des
     *       résultats (compatibilité, même principe que {@code
     *       ImportService#enrichActivity}, ADR-0005).</li>
     * </ul>
     * {@code date} n'a, volontairement, aucune fenêtre par défaut quand il
     * est absent (contrairement à {@link #findWithinRadius}/{@link
     * #findWithinBounds}, qui restreignent aux 7 prochains jours en
     * l'absence de {@code date} — comportement spécifique aux recherches
     * publiques LL-4005/LL-6004, non repris ici) : cet endpoint reste une
     * liste brute, sans restriction implicite, cohérent avec son
     * comportement historique en l'absence de tout filtre.
     *
     * Fuseau horaire d'une occurrence : {@code COALESCE(location.timezone,
     * 'Europe/Paris')} — même résolution qu'à l'écriture ({@code
     * NormalizationService#resolveZone}), pour comparer {@code
     * occurrence.start_at}/{@code end_at} (instants, {@code TIMESTAMPTZ}) à
     * {@code date} (un jour civil) dans le bon fuseau plutôt que celui, non
     * garanti, du serveur PostgreSQL.
     *
     * {@code primarySort}/{@code secondarySort} : voir la javadoc
     * ci-dessous, inchangée par ce ticket — trie toujours sur les colonnes
     * propres d'{@code activity} ({@code city}/{@code start_date}), y
     * compris pour une activité dont la correspondance provient d'une
     * occurrence dans une ville différente de sa {@code city} par défaut
     * (⚠️ décision à valider : un tri exact par « la ville/la date de
     * l'occurrence effectivement trouvée » demanderait de désigner cette
     * occurrence dans le résultat, hors périmètre — {@code activity.city}/
     * {@code activity.start_date} restent une valeur pertinente dans
     * l'immense majorité des cas, la première/l'unique location connue à
     * l'import, voir {@code NormalizationService#normalize}).
     */
    @Query("""
            SELECT a.* FROM activity a
            WHERE a.status = 'PUBLISHED'
              AND (:city::text IS NULL OR
                   EXISTS (
                       SELECT 1 FROM schedule s
                       JOIN occurrence o ON o.schedule_id = s.id
                       JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
                       WHERE s.activity_id = a.id AND o.status <> 'CANCELLED'
                         AND LOWER(l.city) = LOWER(:city::text)
                   )
                   OR (NOT EXISTS (SELECT 1 FROM schedule s2 WHERE s2.activity_id = a.id)
                       AND LOWER(a.city) = LOWER(:city::text)))
              AND (:date::date IS NULL OR
                   EXISTS (
                       SELECT 1 FROM schedule s
                       JOIN occurrence o ON o.schedule_id = s.id
                       JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
                       WHERE s.activity_id = a.id AND o.status <> 'CANCELLED'
                         AND :date::date BETWEEN
                             (o.start_at AT TIME ZONE COALESCE(l.timezone, 'Europe/Paris'))::date
                             AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE COALESCE(l.timezone,
                                 'Europe/Paris'))::date
                   )
                   OR (NOT EXISTS (SELECT 1 FROM schedule s3 WHERE s3.activity_id = a.id)
                       AND :date::date BETWEEN a.start_date::date AND COALESCE(a.end_date, a.start_date)::date))
            ORDER BY
              CASE WHEN :primarySort = 'city' THEN a.city END ASC,
              CASE WHEN :primarySort = 'date' THEN a.start_date END ASC,
              CASE WHEN :secondarySort = 'city' THEN a.city END ASC,
              CASE WHEN :secondarySort = 'date' THEN a.start_date END ASC,
              a.id ASC
            """)
    List<Activity> findAllFiltered(
            @Param("city") String city,
            @Param("date") LocalDate date,
            @Param("primarySort") String primarySort,
            @Param("secondarySort") String secondarySort);

    Optional<Activity> findById(Long id);

    Activity save(Activity activity);

    /**
     * Recherche géographique PostGIS (LL-4002), rendue occurrence-aware par
     * LL-11012 : activités dont un lieu effectif (voir {@code
     * docs/ADR-0006-recherche-occurrences.md}) se trouve à moins de {@code
     * radiusMeters} mètres du point donné. Distance calculée côté base,
     * résultats triés par distance croissante.
     *
     * Comme {@link #findAllFiltered}, une activité correspond via ses
     * occurrences non annulées (lieu effectif {@code
     * COALESCE(occurrence.location_id, schedule.location_id)}) quand elle a
     * au moins un {@code schedule}, sinon via ses colonnes plates
     * historiques ({@code activity.location}, alimentée par le trigger
     * PostGIS existant, {@code V7__add_postgis_location_to_activity.sql}) —
     * voir la javadoc de {@link #findAllFiltered} pour le détail de cette
     * double condition, strictement le même principe ici.
     *
     * {@code categoriesCsv} : chaque valeur est comparée à la colonne
     * historique {@code activity.category} (texte libre, LL-4004) <b>et</b>
     * aux codes du référentiel ({@code category.code} via
     * {@code activity_category}, V42) — une activité est retenue dès
     * qu'une valeur correspond à l'un ou l'autre. Les codes permettent au
     * frontend de filtrer sur les catégories répertoriées, plusieurs à la
     * fois.
     *
     * Filtres {@code status}/{@code categoriesCsv} inchangés (LL-4003/
     * LL-4004, colonnes propres d'{@code activity}, non concernées par ce
     * ticket). Filtre {@code date}/{@code dateTo} (LL-4005/LL-11003) :
     * même sémantique que jusqu'ici (voir la version précédente de cette
     * javadoc, conservée pour le repli sans {@code schedule}), désormais
     * évaluée sur {@code occurrence.start_at}/{@code end_at} (fuseau
     * horaire du lieu, voir {@link #findAllFiltered}) pour la branche
     * occurrence-aware — y compris la fenêtre par défaut des 7 prochains
     * jours en l'absence de {@code date} (LL-4005/LL-6004, même règle dans
     * les deux branches). Cette fenêtre retient une occurrence qui la
     * <b>chevauche</b> (début au plus tard dans 7 jours, fin aujourd'hui ou
     * plus tard) : avant, il fallait que son début ou sa fin y tombe, ce
     * qui masquait une exposition commencée avant aujourd'hui et finissant
     * après la fenêtre (ADR-0011, § 1.3, défaut 3). Même règle pour
     * {@link #findWithinBounds}.
     *
     * Tri par distance : la distance retenue pour une activité est la plus
     * petite parmi celles de ses occurrences correspondantes (au cas où
     * plusieurs occurrences d'une même activité, dans des lieux
     * différents, seraient toutes dans le rayon — « plusieurs villes »,
     * LL-11012) et celle de sa localisation historique propre le cas
     * échéant (repli) — {@code LEAST}/{@code COALESCE} avec {@code
     * 'Infinity'} en absence de correspondance sur une branche, pour ne
     * jamais faire échouer le calcul quand une seule des deux branches
     * s'applique à une activité donnée.
     */
    @Query("""
            SELECT a.* FROM activity a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:categoriesCsv IS NULL
                   OR a.category = ANY(string_to_array(:categoriesCsv, ','))
                   OR EXISTS (
                       SELECT 1 FROM activity_category ac
                       JOIN category c ON c.id = ac.category_id
                       WHERE ac.activity_id = a.id
                         AND c.code = ANY(string_to_array(:categoriesCsv, ','))))
              AND (
                EXISTS (
                    SELECT 1 FROM schedule s
                    JOIN occurrence o ON o.schedule_id = s.id
                    JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
                    WHERE s.activity_id = a.id AND o.status <> 'CANCELLED'
                      AND l.geo_location IS NOT NULL
                      AND ST_DWithin(l.geo_location,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
                      AND ((:date::date IS NULL
                            AND (o.start_at AT TIME ZONE COALESCE(l.timezone, 'Europe/Paris'))::date
                                  <= (now() AT TIME ZONE 'Europe/Paris')::date + INTERVAL '7 days'
                            AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE COALESCE(l.timezone,
                                  'Europe/Paris'))::date >= (now() AT TIME ZONE 'Europe/Paris')::date)
                           OR (:date::date IS NOT NULL AND :dateTo::date IS NULL
                               AND :date::date BETWEEN
                                   (o.start_at AT TIME ZONE COALESCE(l.timezone, 'Europe/Paris'))::date
                                   AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE COALESCE(l.timezone,
                                       'Europe/Paris'))::date)
                           OR (:date::date IS NOT NULL AND :dateTo::date IS NOT NULL
                               AND (o.start_at AT TIME ZONE COALESCE(l.timezone, 'Europe/Paris'))::date <= :dateTo::date
                               AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE COALESCE(l.timezone,
                                   'Europe/Paris'))::date
                                   >= :date::date))
                )
                OR (
                    NOT EXISTS (SELECT 1 FROM schedule s2 WHERE s2.activity_id = a.id)
                    AND a.location IS NOT NULL
                    AND ST_DWithin(a.location,
                          ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
                    AND ((:date::date IS NULL
                          AND a.start_date::date <= (now() AT TIME ZONE 'Europe/Paris')::date + INTERVAL '7 days'
                          AND COALESCE(a.end_date, a.start_date)::date >= (now() AT TIME ZONE 'Europe/Paris')::date)
                         OR (:date::date IS NOT NULL AND :dateTo::date IS NULL
                             AND :date::date BETWEEN a.start_date::date AND COALESCE(a.end_date, a.start_date)::date)
                         OR (:date::date IS NOT NULL AND :dateTo::date IS NOT NULL
                             AND a.start_date::date <= :dateTo::date
                             AND COALESCE(a.end_date, a.start_date)::date >= :date::date))
                )
              )
            ORDER BY LEAST(
                COALESCE((
                    SELECT MIN(ST_Distance(l.geo_location,
                                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography))
                    FROM schedule s JOIN occurrence o ON o.schedule_id = s.id
                    JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
                    WHERE s.activity_id = a.id AND o.status <> 'CANCELLED'
                      AND l.geo_location IS NOT NULL
                      AND ST_DWithin(l.geo_location,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
                ), 'Infinity'::float8),
                COALESCE(
                    CASE WHEN a.location IS NOT NULL
                         THEN ST_Distance(a.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)
                    END, 'Infinity'::float8)
            )
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
     * Recherche par zone cartographique PostGIS (LL-4007), rendue
     * occurrence-aware par LL-11012 selon exactement le même principe que
     * {@link #findWithinRadius} (voir sa javadoc pour le détail des deux
     * branches occurrence/repli) — seul le test géographique change
     * ({@code &&}/{@code ST_MakeEnvelope} plutôt que {@code ST_DWithin}
     * sur un point+rayon, comme avant ce ticket).
     *
     * Pas de tri par distance (pas de point de référence unique pour une
     * zone) : résultats triés par {@code id} croissant, inchangé.
     *
     * Zone de plus de 180° de longitude (carte dézoomée à l'échelle du
     * monde) : en {@code geography}, une telle enveloppe est interprétée par
     * son « petit côté » autour du globe et ne contient plus rien — on ne
     * filtre alors que sur la latitude, toutes les longitudes étant
     * visibles. Le cas courant garde la comparaison {@code geography} (et
     * son index).
     */
    @Query("""
            SELECT a.* FROM activity a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:categoriesCsv IS NULL
                   OR a.category = ANY(string_to_array(:categoriesCsv, ','))
                   OR EXISTS (
                       SELECT 1 FROM activity_category ac
                       JOIN category c ON c.id = ac.category_id
                       WHERE ac.activity_id = a.id
                         AND c.code = ANY(string_to_array(:categoriesCsv, ','))))
              AND (
                EXISTS (
                    SELECT 1 FROM schedule s
                    JOIN occurrence o ON o.schedule_id = s.id
                    JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
                    WHERE s.activity_id = a.id AND o.status <> 'CANCELLED'
                      AND l.geo_location IS NOT NULL
                      AND ((:neLongitude - :swLongitude < 180
                            AND l.geo_location && ST_MakeEnvelope(
                                :swLongitude, :swLatitude, :neLongitude, :neLatitude, 4326)::geography)
                           OR (:neLongitude - :swLongitude >= 180
                            AND ST_Y(l.geo_location::geometry) BETWEEN :swLatitude AND :neLatitude))
                      AND ((:date::date IS NULL
                            AND (o.start_at AT TIME ZONE COALESCE(l.timezone, 'Europe/Paris'))::date
                                  <= (now() AT TIME ZONE 'Europe/Paris')::date + INTERVAL '7 days'
                            AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE COALESCE(l.timezone,
                                  'Europe/Paris'))::date >= (now() AT TIME ZONE 'Europe/Paris')::date)
                           OR (:date::date IS NOT NULL AND :dateTo::date IS NULL
                               AND :date::date BETWEEN
                                   (o.start_at AT TIME ZONE COALESCE(l.timezone, 'Europe/Paris'))::date
                                   AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE COALESCE(l.timezone,
                                       'Europe/Paris'))::date)
                           OR (:date::date IS NOT NULL AND :dateTo::date IS NOT NULL
                               AND (o.start_at AT TIME ZONE COALESCE(l.timezone, 'Europe/Paris'))::date <= :dateTo::date
                               AND (COALESCE(o.end_at, o.start_at) AT TIME ZONE COALESCE(l.timezone,
                                   'Europe/Paris'))::date
                                   >= :date::date))
                )
                OR (
                    NOT EXISTS (SELECT 1 FROM schedule s2 WHERE s2.activity_id = a.id)
                    AND a.location IS NOT NULL
                    AND ((:neLongitude - :swLongitude < 180
                          AND a.location && ST_MakeEnvelope(
                              :swLongitude, :swLatitude, :neLongitude, :neLatitude, 4326)::geography)
                         OR (:neLongitude - :swLongitude >= 180
                          AND ST_Y(a.location::geometry) BETWEEN :swLatitude AND :neLatitude))
                    AND ((:date::date IS NULL
                          AND a.start_date::date <= (now() AT TIME ZONE 'Europe/Paris')::date + INTERVAL '7 days'
                          AND COALESCE(a.end_date, a.start_date)::date >= (now() AT TIME ZONE 'Europe/Paris')::date)
                         OR (:date::date IS NOT NULL AND :dateTo::date IS NULL
                             AND :date::date BETWEEN a.start_date::date AND COALESCE(a.end_date, a.start_date)::date)
                         OR (:date::date IS NOT NULL AND :dateTo::date IS NOT NULL
                             AND a.start_date::date <= :dateTo::date
                             AND COALESCE(a.end_date, a.start_date)::date >= :date::date))
                )
              )
            ORDER BY a.id
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

    /** Contributions d'un utilisateur, toutes statuts confondus, les plus récentes d'abord. */
    List<Activity> findByCreatedByOrderByIdDesc(Long createdBy);

    void deleteById(Long id);

    /**
     * Activités likées par un utilisateur (LS-002), les plus
     * récemment likées en premier. Vit ici plutôt que sur
     * {@code ActivityLikeRepository} (module {@code like}) : la sortie
     * est une {@link Activity} complète (le module {@code like} ne
     * dépend d'aucun autre module au niveau du domaine, voir la javadoc
     * de {@code ActivityLike}) — cette requête est un besoin de lecture
     * côté {@code activity}, pas une opération sur la relation de like
     * elle-même.
     *
     * Filtre {@code status = 'PUBLISHED'} : répond au critère
     * d'acceptation « si un événement liké est annulé [...] il ne doit
     * plus apparaître comme événement actif » (voir aussi
     * {@code V29__create_activity_likes_table.sql}) — le like lui-même
     * n'est pas supprimé quand l'activité passe {@code CANCELLED} (ou
     * tout statut non {@code PUBLISHED}), simplement filtré ici à la
     * lecture ; il réapparaîtrait naturellement si l'activité était de
     * nouveau publiée (aucune fonctionnalité de ce type n'existe
     * actuellement, mais rien n'empêche cette requête de le refléter).
     */
    @Query("""
            SELECT a.* FROM activity a
            JOIN activity_likes l ON l.activity_id = a.id
            WHERE l.user_id = :userId AND a.status = 'PUBLISHED'
            ORDER BY l.created_at DESC
            """)
    List<Activity> findLikedByUserId(@Param("userId") Long userId);

}
