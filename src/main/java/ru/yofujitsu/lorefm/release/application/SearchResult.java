package ru.yofujitsu.lorefm.release.application;

import ru.yofujitsu.lorefm.release.domain.Release;
import java.util.List;

public record SearchResult(List<Release> results, boolean partial, boolean unavailable) {
    public SearchResult { results = List.copyOf(results); }
}
