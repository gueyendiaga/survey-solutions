package com.surveysolutions.analytics.surveysolutions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Dates renvoyées par HQ : UTC, avec ou sans fuseau explicite selon l'API et la version. */
public final class HqDates {
    private HqDates() {}

    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // Pas de fuseau : HQ fournit des dates UTC.
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
