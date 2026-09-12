package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.ExternalActivity;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repository ExternalActivity (LL-11010). Même patron que les autres
 * repositories du sprint. {@link #findBySourceIdAndExternalId} est la
 * clé métier de l'entité (voir la javadoc de {@link ExternalActivity}),
 * utilisée par {@code ExternalActivityService#upsert} pour retrouver un
 * élément déjà collecté.
 */
public interface ExternalActivityRepository extends Repository<ExternalActivity, Long> {

    Optional<ExternalActivity> findBySourceIdAndExternalId(Long sourceId, String externalId);

    Optional<ExternalActivity> findById(Long id);

    ExternalActivity save(ExternalActivity externalActivity);
}
