package com.locallife.backend.schedule.infrastructure;

import com.locallife.backend.schedule.domain.Occurrence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Occurrence (LL-11004). Même patron que
 * {@code ScheduleRepository} : étend {@link Repository} (interface
 * marqueur), n'expose que les méthodes listées ici.
 * {@link #findByScheduleId} sert le critère d'acceptation « relation
 * avec Schedule » (un schedule récurrent se matérialise en plusieurs
 * occurrences).
 */
public interface OccurrenceRepository extends Repository<Occurrence, Long> {

    List<Occurrence> findAll();

    Optional<Occurrence> findById(Long id);

    List<Occurrence> findByScheduleId(Long scheduleId);

    Occurrence save(Occurrence occurrence);
}
