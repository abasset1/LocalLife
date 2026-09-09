package com.locallife.backend.schedule.application;

import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.schedule.infrastructure.ScheduleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Schedule (LL-11002). Délégation simple vers le repository,
 * même patron que {@code LocationService} pour ses méthodes de base.
 * Contrairement à {@code LocationService#create}, aucun garde-fou
 * applicatif supplémentaire ici : le ticket ne demande explicitement
 * qu'une intégrité référentielle (« chaque schedule peut avoir son
 * propre lieu »), déjà garantie par les contraintes {@code FOREIGN KEY}
 * de {@code V18__create_schedule_table.sql} — pas de règle métier
 * équivalente à « aucun lieu créé à partir de données insuffisantes »
 * énoncée pour Schedule, donc rien à valider ici en plus (ne pas
 * anticiper, voir AI_RULES.md).
 */
@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    public ScheduleService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    public List<Schedule> findAll() {
        return scheduleRepository.findAll();
    }

    public Optional<Schedule> findById(Long id) {
        return scheduleRepository.findById(id);
    }

    public List<Schedule> findByActivityId(Long activityId) {
        return scheduleRepository.findByActivityId(activityId);
    }

    public Schedule create(Schedule schedule) {
        return scheduleRepository.save(schedule);
    }
}
