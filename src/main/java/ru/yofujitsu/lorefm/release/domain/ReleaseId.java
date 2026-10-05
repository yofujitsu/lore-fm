package ru.yofujitsu.lorefm.release.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public record ReleaseId(@JsonValue String value) {
    public ReleaseId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Release id is required");
    }
}
