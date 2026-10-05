package ru.yofujitsu.lorefm.release.application;

import ru.yofujitsu.lorefm.release.domain.Provider;
import java.util.Set;

public record ReleaseSearchQuery(String query, int limit, Set<Provider> providers) {
    public ReleaseSearchQuery {
        query = query == null ? "" : query.replaceAll("^[\\p{IsWhite_Space}\\u001c-\\u001f\\ufeff]+|[\\p{IsWhite_Space}\\u001c-\\u001f\\ufeff]+$", "");
        int length = query.codePointCount(0, query.length());
        if (length < 2 || length > 200) {
            throw new InvalidSearchException("INVALID_SEARCH_QUERY", "Search query must contain between 2 and 200 characters.");
        }
        if (limit < 1 || limit > 20) {
            throw new InvalidSearchException("INVALID_SEARCH_LIMIT", "Search limit must be between 1 and 20.");
        }
        providers = Set.copyOf(providers);
    }
}
