package com.locallife.backend.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.locallife.backend.schedule.domain.Occurrence;
import com.locallife.backend.schedule.infrastructure.OccurrenceRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la délégation de {@link OccurrenceService} vers
 * {@link OccurrenceRepository} (LL-11004), ainsi que {@link
 * OccurrenceService#update}/{@link OccurrenceService#cancel}/{@link
 * OccurrenceService#findEffectiveByScheduleId} (LL-11005).
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
        return new Occurrence(id, scheduleId, startAt, startAt.plusSeconds(9000), 10L, "SCHEDULED", false);
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
    void findEffectiveByScheduleId_ShouldExcludeCancelledOccurrences() {
        // Critère d'acceptation LL-11005 « les recherches utilisent l'état effectif de l'occurrence ».
        Occurrence scheduled = occurrence(1L, 42L, Instant.parse("2026-09-08T09:30:00Z"));
        when(occurrenceRepository.findByScheduleIdAndStatusNot(42L, OccurrenceService.STATUS_CANCELLED))
                .thenReturn(List.of(scheduled));

        List<Occurrence> result = occurrenceService.findEffectiveByScheduleId(42L);

        assertThat(result).containsExactly(scheduled);
    }

    @Test
    void update_ShouldDelegateToRepository_WhenIdIsPresent() {
        // LL-11005 : modification d'horaire / déplacement / changement de lieu — simple changement
        // de champs sur l'occurrence, jamais sur le schedule (voir la javadoc de la méthode).
        Occurrence moved = new Occurrence(
                1L, 42L, Instant.parse("2026-09-22T10:00:00Z"), Instant.parse("2026-09-22T12:30:00Z"),
                99L, "SCHEDULED", true);
        when(occurrenceRepository.save(moved)).thenReturn(moved);

        Occurrence result = occurrenceService.update(moved);

        assertThat(result).isEqualTo(moved);
    }

    @Test
    void update_ShouldThrow_WhenIdIsAbsent() {
        Occurrence withoutId = occurrence(null, 42L, Instant.parse("2026-09-08T09:30:00Z"));

        assertThatThrownBy(() -> occurrenceService.update(withoutId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_ShouldSetCancelledStatusAndExceptionalFlag_KeepingOtherFields() {
        // LL-11005, « 15 septembre → annulé » : le statut et le drapeau d'exception changent,
        // mais l'horaire/lieu d'origine restent lisibles (voir la javadoc de la méthode).
        Instant startAt = Instant.parse("2026-09-15T09:30:00Z");
        Occurrence existing = occurrence(1L, 42L, startAt);
        when(occurrenceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(occurrenceRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        Occurrence result = occurrenceService.cancel(1L);

        assertThat(result.status()).isEqualTo(OccurrenceService.STATUS_CANCELLED);
        assertThat(result.exceptional()).isTrue();
        assertThat(result.startAt()).isEqualTo(startAt);
        assertThat(result.locationId()).isEqualTo(existing.locationId());
    }

    @Test
    void cancel_ShouldThrow_WhenOccurrenceDoesNotExist() {
        when(occurrenceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> occurrenceService.cancel(999L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findAll_ShouldDelegateToRepository() {
        Occurrence found = occurrence(1L, 1L, Instant.parse("2026-09-08T09:30:00Z"));
        when(occurrenceRepository.findAll()).thenReturn(List.of(found));

        List<Occurrence> result = occurrenceService.findAll();

        assertThat(result).containsExactly(found);
    }
}
