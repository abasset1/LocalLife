package com.locallife.backend.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la délégation de {@link OccurrenceService} vers
 * {@link OccurrenceRepository} (LL-11004) — pas de garde-fou applicatif
 * à tester ici, voir la javadoc de {@link OccurrenceService}.
 */
@ExtendWith(MockitoExtension.class)
class OccurrenceServiceTest {

    @Mock
    private OccurrenceRepository occurrenceRepository;

    private OccurrenceService occurrenceService;

    @BeforeEach
    void setUp() {
        occurrenceService = new OccurrenceService(occurrenceRepository);
    }

    private Occurrence occurrence(Long id, Long scheduleId, Instant startAt) {
        return new Occurrence(id, scheduleId, startAt, startAt.plusSeconds(9000), 10L, "SCHEDULED");
    }

    @Test
    void create_ShouldDelegateToRepository() {
        Instant startAt = Instant.parse("2026-09-08T09:30:00Z");
        Occurrence toCreate = occurrence(null, 1L, startAt);
        Occurrence saved = occurrence(1L, 1L, startAt);
        when(occurrenceRepository.save(toCreate)).thenReturn(saved);

        Occurrence result = occurrenceService.create(toCreate);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void findById_ShouldDelegateToRepository() {
        Occurrence found = occurrence(1L, 1L, Instant.parse("2026-09-08T09:30:00Z"));
        when(occurrenceRepository.findById(1L)).thenReturn(Optional.of(found));

        Optional<Occurrence> result = occurrenceService.findById(1L);

        assertThat(result).contains(found);
    }

    @Test
    void findByScheduleId_ShouldReturnAllOccurrencesForSchedule() {
        // Couvre directement le critère d'acceptation « relation avec Schedule ».
        Occurrence first = occurrence(1L, 42L, Instant.parse("2026-09-08T09:30:00Z"));
        Occurrence second = occurrence(2L, 42L, Instant.parse("2026-09-15T09:30:00Z"));
        when(occurrenceRepository.findByScheduleId(42L)).thenReturn(List.of(first, second));

        List<Occurrence> result = occurrenceService.findByScheduleId(42L);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void findAll_ShouldDelegateToRepository() {
        Occurrence found = occurrence(1L, 1L, Instant.parse("2026-09-08T09:30:00Z"));
        when(occurrenceRepository.findAll()).thenReturn(List.of(found));

        List<Occurrence> result = occurrenceService.findAll();

        assertThat(result).containsExactly(found);
    }
}
