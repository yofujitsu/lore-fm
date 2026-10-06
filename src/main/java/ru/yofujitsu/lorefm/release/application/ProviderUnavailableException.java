package ru.yofujitsu.lorefm.release.application;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** Safe cooldown information; never includes external response bodies. */
public class ProviderUnavailableException extends RuntimeException {
    private final Duration retryAfter;
    public ProviderUnavailableException(Duration retryAfter) {
        super("Provider temporarily unavailable");
        this.retryAfter = retryAfter;
    }
    public Duration retryAfter() { return retryAfter; }

    public static Duration retryAfter(String header) {
        Duration delay = Duration.ofSeconds(30);
        if (header != null) {
            try { delay = Duration.ofSeconds(Long.parseLong(header.strip())); }
            catch (NumberFormatException ex) {
                try { delay = Duration.between(Instant.now(), ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()); }
                catch (DateTimeException ignored) { /* Use a bounded default for malformed Retry-After. */ }
            }
        }
        if (delay.compareTo(Duration.ofSeconds(1)) < 0) return Duration.ofSeconds(1);
        return delay.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1) : delay;
    }
}
