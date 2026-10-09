package com.locallife.backend.schedule.application;

import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service Occurrence (LL-11004/LL-11005). Délégation simple vers le
 * repository, même patron que {@code ScheduleService} pour ses méthodes
 * de base. Aucun garde-fou applicatif sur {@link #create}/{@link
 * #update} : l'intégrité référentielle (« relation avec Schedule ») est
 * garantie par la contrainte {@code FOREIGN KEY} de {@code
 * V19__create_occurrence_table.sql}. La matérialisation effective
 * d'occurrences « normales » à partir d'un {@code Schedule} (appel à
 * {@code RecurrenceRuleService}) est du ressort d'un ticket ultérieur —
 * ce service ne fait que persister des {@code Occurrence} déjà
 * construites par l'appelant.
 *
 * {@code CANCELLED}/{@code SCHEDULED} : valeurs de {@code
 * Occurrence#status} utilisées par ce service, voir la javadoc de
 * {@code Occurrence}.
 */
@Service
public class OccurrenceService {

    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** Nombre d'occurrences envoyées à la base par aller-retour, voir {@link #createAll}. */
    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL =
            "INSERT INTO occurrence (schedule_id, start_at, end_at, location_id, status, is_exception) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private final OccurrenceRepository occurrenceRepository;
    private final JdbcTemplate jdbcTemplate;

    public OccurrenceService(OccurrenceRepository occurrenceRepository, JdbcTemplate jdbcTemplate) {
        this.occurrenceRepository = occurrenceRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Occurrence> findAll() {
        return occurrenceRepository.findAll();
    }

    public Optional<Occurrence> findById(Long id) {
        return occurrenceRepository.findById(id);
    }

    public List<Occurrence> findByScheduleId(Long scheduleId) {
        return occurrenceRepository.findByScheduleId(scheduleId);
    }

    /**
     * Occurrences « effectives » d'un schedule (LL-11005, critère
     * d'acceptation « les recherches utilisent l'état effectif de
     * l'occurrence ») : toutes sauf les annulées — leur {@code status}/
     * {@code startAt}/{@code locationId} déjà persistés reflètent
     * directement toute exception (annulation, déplacement, changement
     * de lieu), sans recalcul depuis {@code Schedule}.
     */
    public List<Occurrence> findEffectiveByScheduleId(Long scheduleId) {
        return occurrenceRepository.findByScheduleIdAndStatusNot(scheduleId, STATUS_CANCELLED);
    }

    public Occurrence create(Occurrence occurrence) {
        return occurrenceRepository.save(occurrence);
    }

    /**
     * Supprime les occurrences d'un schedule (LL-11011) : voir la
     * javadoc de {@code OccurrenceRepository#deleteByScheduleId}.
     */
    /**
     * Crée plusieurs occurrences en insertions groupées ({@value #BATCH_SIZE} par aller-retour), sans
     * relire les identifiants générés.
     *
     * <p>Pour l'import : une activité peut porter plusieurs centaines de créneaux (765 pour une visite
     * guidée quotidienne, import du 09/10/2026), et {@link #create} coûte un aller-retour par occurrence :
     * mesuré à 7,6 s pour 2 000 occurrences une par une, contre 0,16 s groupées.
     */
    public void createAll(List<Occurrence> occurrences) {
        if (occurrences == null || occurrences.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, occurrences, BATCH_SIZE, (statement, occurrence) -> {
            statement.setLong(1, occurrence.scheduleId());
            statement.setObject(2, toOffsetDateTime(occurrence.startAt()));
            statement.setObject(3, toOffsetDateTime(occurrence.endAt()));
            if (occurrence.locationId() == null) {
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(4, occurrence.locationId());
            }
            statement.setString(5, occurrence.status());
            statement.setBoolean(6, occurrence.exceptional());
        });
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public void deleteByScheduleId(Long scheduleId) {
        occurrenceRepository.deleteByScheduleId(scheduleId);
    }

    /**
     * Modifie une occurrence existante (LL-11005 : modification
     * d'horaire, déplacement, changement de lieu — tous représentés par
     * des valeurs de champs différentes, voir la javadoc de {@code
     * Occurrence}). Ne modifie jamais {@link
     * com.locallife.backend.schedule.domain.Schedule} (tables séparées,
     * critère d'acceptation « elle ne détruit pas la règle générale ») :
     * cette méthode n'agit que sur la ligne {@code Occurrence} fournie.
     *
     * @throws IllegalArgumentException si {@code occurrence.id()} est null
     */
    public Occurrence update(Occurrence occurrence) {
        if (occurrence.id() == null) {
            throw new IllegalArgumentException("Impossible de modifier une occurrence sans id.");
        }
        return occurrenceRepository.save(occurrence);
    }

    /**
     * Annule une occurrence (LL-11005, « 15 septembre → annulé ») :
     * {@code status = CANCELLED}, {@code exceptional = true}. Les autres
     * champs (horaire, lieu d'origine) sont conservés tels quels plutôt
     * qu'effacés — permet de savoir quand/où l'occurrence annulée était
     * initialement prévue.
     *
     * @throws java.util.NoSuchElementException si aucune occurrence n'existe pour {@code occurrenceId}
     */
    public Occurrence cancel(Long occurrenceId) {
        Occurrence existing = occurrenceRepository.findById(occurrenceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Occurrence introuvable : " + occurrenceId));
        Occurrence cancelled = new Occurrence(
                existing.id(), existing.scheduleId(), existing.startAt(), existing.endAt(),
                existing.locationId(), STATUS_CANCELLED, true);
        return occurrenceRepository.save(cancelled);
    }
}
