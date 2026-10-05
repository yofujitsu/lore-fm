package ru.yofujitsu.lorefm.release.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.yofujitsu.lorefm.release.application.*;
import ru.yofujitsu.lorefm.release.domain.Provider;

import java.util.HashSet;
import java.util.Set;

@RestController
public class ReleaseController {
    private final ReleaseSearchService service;
    public ReleaseController(ReleaseSearchService service) { this.service = service; }

    @GetMapping("/api/v1/releases/search")
    public SearchResponse search(@RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "10") String limit,
                                 @RequestParam(required = false) String providers) {
        int parsedLimit;
        try { parsedLimit = Integer.parseInt(limit); }
        catch (NumberFormatException ex) {
            throw new InvalidSearchException("INVALID_SEARCH_LIMIT", "Search limit must be between 1 and 20.");
        }
        Set<Provider> selected = new HashSet<>();
        if (providers != null) {
            try {
                for (String name : providers.split(",", -1)) selected.add(Provider.valueOf(name.strip()));
            } catch (IllegalArgumentException ex) {
                throw new InvalidSearchException("INVALID_SEARCH_PROVIDER", "Unknown search provider.");
            }
        }
        var query = new ReleaseSearchQuery(q, parsedLimit, selected);
        return new SearchResponse(query.query(), service.search(query));
    }
}
