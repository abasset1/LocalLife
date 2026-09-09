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
}
