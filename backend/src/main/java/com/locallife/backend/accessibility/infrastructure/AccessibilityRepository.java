package com.locallife.backend.accessibility.infrastructure;

import com.locallife.backend.accessibility.domain.Accessibility;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Accessibility (LL-11009). Même patron que les autres
 * repositories du sprint. {@link #findByActivityId} plutôt qu'un simple
 * {@code findById} en usage courant : la relation avec {@code Activity}
 * est un-à-un, c'est {@code activityId} que les appelants connaissent
 * (pas l'id propre à {@code Accessibility}).
 */
public interface AccessibilityRepository extends Repository<Accessibility, Long> {

    Optional<Accessibility> findByActivityId(Long activityId);

    Optional<Accessibility> findById(Long id);

    Accessibility save(Accessibility accessibility);
}
