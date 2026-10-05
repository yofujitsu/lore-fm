package ru.yofujitsu.lorefm.release.domain;

public record Artist(String name) {
    public Artist {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Artist name is required");
    }
}
