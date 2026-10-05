package com.locallife.backend.activityclassification.infrastructure;

import com.locallife.backend.activityclassification.domain.ClassificationRecord;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ClassificationRecordRepository extends Repository<ClassificationRecord, Long> {

    Optional<ClassificationRecord> findBySourceIdAndImportKey(Long sourceId, String importKey);

    Optional<ClassificationRecord> findByActivityId(Long activityId);

    List<ClassificationRecord> findByStatus(ClassificationStatus status);

    ClassificationRecord save(ClassificationRecord record);

}
