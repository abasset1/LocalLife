package com.locallife.backend.schedule.application;

import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
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

    private final OccurrenceRepository occurrenceRepository;

    public OccurrenceService(OccurrenceRepository occurrenceRepository) {
        this.occurrenceRepository = occurrenceRepository;
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
