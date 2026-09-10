package com.locallife.backend.schedule.infrastructure;

import com.locallife.backend.schedule.domain.Occurrence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Occurrence (LL-11004/LL-11005). Même patron que
 * {@code ScheduleRepository} : étend {@link Repository} (interface
 * marqueur), n'expose que les méthodes listées ici.
 * {@link #findByScheduleId} sert le critère d'acceptation « relation
 * avec Schedule » (un schedule récurrent se matérialise en plusieurs
 * occurrences). {@link #findByScheduleIdAndStatusNot} sert le critère
 * d'acceptation de LL-11005 « les recherches utilisent l'état effectif
 * de l'occurrence » : permet à un futur appelant (recherche/calendrier)
 * d'exclure les occurrences annulées sans avoir à recalculer quoi que
 * ce soit depuis {@code Schedule} — l'état effectif est déjà porté par
 * la ligne {@code Occurrence} elle-même (voir la javadoc d'{@code
 * Occurrence}).
 */
public interface OccurrenceRepository extends Repository<Occurrence, Long> {

    List<Occurrence> findAll();

    Optional<Occurrence> findById(Long id);

    List<Occurrence> findByScheduleId(Long scheduleId);

    List<Occurrence> findByScheduleIdAndStatusNot(Long scheduleId, String status);

    Occurrence save(Occurrence occurrence);
}
