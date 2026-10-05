package ru.yofujitsu.lorefm.release.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.yofujitsu.lorefm.release.domain.Provider;
import ru.yofujitsu.lorefm.release.domain.Release;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReleaseSearchService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseSearchService.class);
    private record CacheKey(Provider provider, String query, int limit) {}
    private final List<ReleaseProvider> providers;
    private final Cache<CacheKey, List<ProviderRelease>> cache;

    public ReleaseSearchService(List<ReleaseProvider> providers,
                                @Value("${search.cache.ttl:15m}") Duration ttl,
                                @Value("${search.cache.maximum-size:1000}") long maximumSize) {
        this.providers = List.copyOf(providers);
        this.cache = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).build();
    }

    public List<Release> search(ReleaseSearchQuery query) {
        var results = new ArrayList<Release>();
        for (var provider : providers) {
            if (!query.providers().isEmpty() && !query.providers().contains(provider.provider())) continue;
            var key = new CacheKey(provider.provider(), query.query(), query.limit());
            try {
                var found = cache.getIfPresent(key);
                if (found == null) {
                    found = List.copyOf(provider.search(query));
                    cache.put(key, found);
                }
                found.stream().map(ProviderRelease::release).forEach(results::add);
            } catch (RuntimeException ex) {
                // Never log provider exception messages: they can contain response bodies or credentials.
                log.warn("Provider search failed: provider={} category={}", provider.provider(), ex.getClass().getSimpleName());
            }
        }
        return results.stream().limit(query.limit()).toList();
    }
}
