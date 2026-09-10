package com.locallife.backend.schedule.application;

import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Occurrence (LL-11004). Délégation simple vers le repository,
 * même patron que {@code ScheduleService} pour ses méthodes de base.
 * Aucun garde-fou applicatif supplémentaire : l'intégrité référentielle
 * (« relation avec Schedule ») est garantie par la contrainte
 * {@code FOREIGN KEY} de {@code V19__create_occurrence_table.sql}. La
 * matérialisation effective d'occurrences à partir d'un
 * {@code Schedule} (appel à {@code RecurrenceRuleService}) est du
 * ressort d'un ticket ultérieur — ce service ne fait que persister des
 * {@code Occurrence} déjà construites par l'appelant (voir la javadoc
 * de {@code Occurrence}).
 */
@Service
public class OccurrenceService {

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

    public Occurrence create(Occurrence occurrence) {
        return occurrenceRepository.save(occurrence);
    }
}
