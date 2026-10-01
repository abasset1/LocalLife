package com.locallife.backend.organizertype.infrastructure;

import com.locallife.backend.organizertype.domain.OrganizerType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository OrganizerType — uniquement des opérations de lecture
 * (CAT-004 : les 9 types V1 sont créés une fois pour toutes par
 * migration — même convention que les trois référentiels précédents).
 */
public interface OrganizerTypeRepository extends Repository<OrganizerType, Long> {

    List<OrganizerType> findAll();

    Optional<OrganizerType> findById(Long id);

    Optional<OrganizerType> findByCode(String code);

}
