package com.locallife.backend.activity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContributionRecurrenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Test
    void of_ShouldBuildWeeklyRule_WithDaysInWeekOrderAndWithoutDuplicates() {
        ContributionRecurrence recurrence =
                ContributionRecurrence.of(List.of("th", "TU", "TH"), "11:30", "14:00", null, null, TODAY);

        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", recurrence.recurrenceRule());
        assertEquals(LocalTime.of(11, 30), recurrence.startTime());
        assertEquals(LocalTime.of(14, 0), recurrence.endTime());
        assertEquals(TODAY, recurrence.from());
        assertNull(recurrence.until());
    }

    @Test
    void of_ShouldRejectMissingDays() {
        assertThrows(IllegalArgumentException.class,
                () -> ContributionRecurrence.of(List.of(), "11:30", null, null, null, TODAY));
    }

    @Test
    void of_ShouldRejectUnknownDay() {
        assertThrows(IllegalArgumentException.class,
                () -> ContributionRecurrence.of(List.of("XX"), "11:30", null, null, null, TODAY));
    }

    @Test
    void of_ShouldRejectMissingOrInvalidStartTime() {
        assertThrows(IllegalArgumentException.class,
                () -> ContributionRecurrence.of(List.of("MO"), " ", null, null, null, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> ContributionRecurrence.of(List.of("MO"), "25:00", null, null, null, TODAY));
    }

    @Test
    void of_ShouldRejectUntilBeforeFrom() {
        assertThrows(IllegalArgumentException.class, () -> ContributionRecurrence.of(
                List.of("MO"), "11:30", null, "2026-10-10", "2026-10-09", TODAY));
    }
}
