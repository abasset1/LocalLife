package com.locallife.backend.schedule.infrastructure;

import com.locallife.backend.schedule.domain.Schedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Schedule (LL-11002). Même patron que
 * {@code ActivityRepository}/{@code LocationRepository} : étend
 * {@link Repository} (interface marqueur), n'expose que les méthodes
 * listées ici. {@link #findByActivityId} sert directement le critère
 * d'acceptation « une activité peut avoir plusieurs schedules ».
 */
public interface ScheduleRepository extends Repository<Schedule, Long> {

    List<Schedule> findAll();

    Optional<Schedule> findById(Long id);

    List<Schedule> findByActivityId(Long activityId);

    Schedule save(Schedule schedule);

    /**
     * Supprime les schedules d'une activité (LL-11011) : utilisé par
     * {@code ImportService} pour repartir d'un état propre avant de
     * reconstruire la programmation à chaque réimport — voir sa javadoc
     * (« remplacer plutôt que fusionner »). Les {@code Occurrence}
     * associées doivent être supprimées avant l'appel (pas de {@code ON
     * DELETE CASCADE} en base, voir {@code V19__create_occurrence_table.sql}) —
     * la responsabilité de cet ordre incombe à l'appelant.
     */
    void deleteByActivityId(Long activityId);
}
