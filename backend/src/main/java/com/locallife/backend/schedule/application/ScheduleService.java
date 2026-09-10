package com.locallife.backend.schedule.application;

import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.schedule.infrastructure.ScheduleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Schedule (LL-11002/LL-11003). Délégation simple vers le
 * repository, même patron que {@code LocationService} pour ses méthodes
 * de base. Contrairement à {@code LocationService#create}, pas de
 * garde-fou sur les champs de base : le ticket LL-11002 ne demandait
 * explicitement qu'une intégrité référentielle (« chaque schedule peut
 * avoir son propre lieu »), déjà garantie par les contraintes
 * {@code FOREIGN KEY} de {@code V18__create_schedule_table.sql}.
 * {@link #create} valide en revanche {@code recurrenceRule} quand il est
 * fourni (LL-11003, critère d'acceptation « règle persistée » — implique
 * qu'une règle syntaxiquement invalide ne doit pas l'être).
 */
@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final RecurrenceRuleService recurrenceRuleService;

    public ScheduleService(ScheduleRepository scheduleRepository, RecurrenceRuleService recurrenceRuleService) {
        this.scheduleRepository = scheduleRepository;
        this.recurrenceRuleService = recurrenceRuleService;
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
        if (schedule.recurrenceRule() != null) {
            recurrenceRuleService.validate(schedule.recurrenceRule());
        }
        return scheduleRepository.save(schedule);
    }
}
