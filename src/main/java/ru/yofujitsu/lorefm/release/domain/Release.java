package ru.yofujitsu.lorefm.release.domain;

import java.util.List;
import java.util.Objects;

public record Release(ReleaseId id, String title, List<Artist> artists, Integer releaseYear,
                      ReleaseType type, Artwork artwork, List<ReleaseSource> sources) {
    public Release {
        Objects.requireNonNull(id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Release title is required");
        artists = List.copyOf(artists);
        sources = List.copyOf(sources);
        Objects.requireNonNull(type);
    }
}
