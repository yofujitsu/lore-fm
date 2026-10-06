package ru.yofujitsu.lorefm.release.application;

import ru.yofujitsu.lorefm.release.domain.Release;

public record ProviderRelease(Release release, int confidence) {
    public ProviderRelease(Release release) { this(release, 100); }
    public ProviderRelease {
        java.util.Objects.requireNonNull(release);
        if (confidence < 0 || confidence > 100) throw new IllegalArgumentException("Invalid provider confidence");
    }
}
