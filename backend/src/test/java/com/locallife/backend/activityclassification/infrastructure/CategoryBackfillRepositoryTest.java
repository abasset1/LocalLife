package com.locallife.backend.activityclassification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryBackfillRepositoryTest {

    @Test
    void splitTagsReadsTheJoinedSourceTags() {
        assertThat(CategoryBackfillRepository.splitTags("Marché, fête ,,Noël")).isEqualTo(List.of("Marché", "fête", "Noël"));
        assertThat(CategoryBackfillRepository.splitTags(null)).isEmpty();
        assertThat(CategoryBackfillRepository.splitTags(" ")).isEmpty();
    }
}
