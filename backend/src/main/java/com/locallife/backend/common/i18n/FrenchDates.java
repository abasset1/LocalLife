package com.locallife.backend.common.i18n;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dates en clair des messages envoyés (e-mails, notifications) : « samedi 22 novembre à 20 h 30 ». */
public final class FrenchDates {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private FrenchDates() {
    }

    /**
     * « samedi 18 octobre à 15 h », « samedi 18 octobre à 20 h 30 », sans heure si elle est inconnue (minuit) ;
     * {@code null} sans date.
     */
    public static String dayAndTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        String day = DAY_FORMAT.format(dateTime);
        LocalTime time = dateTime.toLocalTime();
        if (time.equals(LocalTime.MIDNIGHT)) {
            return day;
        }
        String minutes = time.getMinute() == 0 ? "" : String.format(" %02d", time.getMinute());
        return day + " à " + time.getHour() + " h" + minutes;
    }
}
