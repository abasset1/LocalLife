package com.locallife.backend.foodtruck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.foodtruck.domain.PresenceRules.Slot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresenceRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private static Slot slot(String days, String start, String end, String from, String until) {
        return new Slot(days, start == null ? null : LocalTime.parse(start), end == null ? null : LocalTime.parse(end),
                from == null ? null : LocalDate.parse(from), until == null ? null : LocalDate.parse(until));
    }

    @Test
    void days_ShouldBeOrdered_AndAllSevenMeanEveryDay() {
        assertThat(PresenceRules.normalizeDays(List.of("sa", "MO", "SA"))).isEqualTo("MO,SA");
        assertThat(PresenceRules.normalizeDays(List.of())).isNull();
        assertThat(PresenceRules.normalizeDays(PresenceRules.DAY_CODES)).isNull();
        assertThatThrownBy(() -> PresenceRules.normalizeDays(List.of("LUN"))).hasMessageContaining("LUN");
    }

    @Test
    void validation_ShouldRejectInconsistentHoursAndPastPeriods() {
        PresenceRules.validate(slot("SA", "08:00", "13:00", null, null), TODAY);
        assertThatThrownBy(() -> PresenceRules.validate(slot(null, "08:00", null, null, null), TODAY))
                .hasMessageContaining("aucune des deux");
        assertThatThrownBy(() -> PresenceRules.validate(slot(null, "13:00", "08:00", null, null), TODAY))
                .hasMessageContaining("suivre");
        assertThatThrownBy(() -> PresenceRules.validate(slot(null, null, null, "2026-11-02", "2026-11-01"), TODAY))
                .hasMessageContaining("période");
        assertThatThrownBy(() -> PresenceRules.validate(slot(null, null, null, null, "2026-10-18"), TODAY))
                .hasMessageContaining("passée");
    }

    @Test
    void overlap_ShouldNeedACommonDayPeriodAndHours() {
        Slot saturdayMorning = slot("SA", "08:00", "13:00", null, null);
        assertThat(PresenceRules.overlaps(saturdayMorning, slot("SA,SU", "12:00", "14:00", null, null))).isTrue();
        // Bout à bout : 13 h – 18 h ne chevauche pas 8 h – 13 h.
        assertThat(PresenceRules.overlaps(saturdayMorning, slot("SA", "13:00", "18:00", null, null))).isFalse();
        assertThat(PresenceRules.overlaps(saturdayMorning, slot("SU", "08:00", "13:00", null, null))).isFalse();
        // Sans horaires = toute la journée ; sans jours = tous les jours.
        assertThat(PresenceRules.overlaps(saturdayMorning, slot(null, null, null, null, null))).isTrue();
        assertThat(PresenceRules.overlaps(slot("SA", null, null, "2026-11-01", "2026-11-30"),
                slot("SA", null, null, "2026-12-01", null))).isFalse();
        assertThat(PresenceRules.overlaps(slot("SA", null, null, null, "2026-11-30"),
                slot("SA", null, null, "2026-11-30", null))).isTrue();
    }
}
