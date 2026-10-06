package ru.yofujitsu.lorefm.release.api;

import ru.yofujitsu.lorefm.release.domain.Release;
import java.util.List;

public record SearchResponse(String query, List<Release> results, boolean partial, boolean unavailable) {}
