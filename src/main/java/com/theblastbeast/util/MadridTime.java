package com.theblastbeast.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Fechas de la agenda en la zona horaria del público español.
 *
 * <p>Ticketmaster devuelve horas locales y la agenda se muestra siempre en
 * Europe/Madrid, así que todas las conversiones pasan por aquí.
 */
public final class MadridTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private static final DateTimeFormatter TICKETMASTER_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private MadridTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toOffsetDateTime();
    }

    public static OffsetDateTime endOfDayExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
    }

    public static String ticketmasterStart(LocalDate date) {
        return startOfDay(date).withOffsetSameInstant(ZoneOffset.UTC).format(TICKETMASTER_FORMAT);
    }

    public static String ticketmasterEndExclusive(LocalDate date) {
        return endOfDayExclusive(date).withOffsetSameInstant(ZoneOffset.UTC)
                .format(TICKETMASTER_FORMAT);
    }

    public static LocalDate toLocalDate(OffsetDateTime value) {
        return value.atZoneSameInstant(ZONE).toLocalDate();
    }
}
