package com.locallife.backend.audience.infrastructure;

import com.locallife.backend.audience.domain.Audience;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository Audience — uniquement des opérations de lecture (CAT-003 :
 * les 7 publics V1 sont créés une fois pour toutes par migration — même
 * convention que {@code CategoryRepository}/{@code ActivityTypeRepository}).
 */
public interface AudienceRepository extends Repository<Audience, Long> {

    List<Audience> findAll();

    Optional<Audience> findById(Long id);

    Optional<Audience> findByCode(String code);

}
