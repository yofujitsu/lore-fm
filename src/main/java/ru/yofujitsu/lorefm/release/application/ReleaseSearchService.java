package ru.yofujitsu.lorefm.release.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.yofujitsu.lorefm.release.domain.Provider;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class ReleaseSearchService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ReleaseSearchService.class);
    private record CacheKey(Provider provider, String query, int limit) {}
    private final List<ReleaseProvider> providers;
    private final Cache<CacheKey, List<ProviderRelease>> cache;
    private final Duration timeout;
    private final Duration cooldown;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Provider, ProviderState> states = new ConcurrentHashMap<>();
    private final ReleaseAggregator aggregator = new ReleaseAggregator();

    public ReleaseSearchService(List<ReleaseProvider> providers,
                                @Value("${search.cache.ttl:15m}") Duration ttl,
                                @Value("${search.cache.maximum-size:1000}") long maximumSize,
                                @Value("${search.request-timeout:2s}") Duration timeout,
                                @Value("${search.failure-cooldown:30s}") Duration cooldown) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(2)) > 0
                || cooldown.isNegative() || cooldown.isZero() || cooldown.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Search timeout must be within 2 seconds; cooldown within 1 hour");
        }
        this.providers = providers.stream().sorted(Comparator.comparing(ReleaseProvider::provider)).toList();
        this.cache = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).build();
        this.timeout = timeout;
        this.cooldown = cooldown;
    }

    public SearchResult search(ReleaseSearchQuery query) {
        long deadline = System.nanoTime() + timeout.toNanos();
        var active = providers.stream().filter(ReleaseProvider::enabled)
                .filter(p -> query.providers().isEmpty()
                        ? p.provider() == Provider.SPOTIFY || p.provider() == Provider.MUSICBRAINZ
                        : query.providers().contains(p.provider())).toList();
        var tasks = active.stream().map(p -> executor.submit(() -> cached(p, query))).toList();
        var found = new ArrayList<ProviderRelease>();
        int successes = 0;
        try {
            for (int i = 0; i < tasks.size(); i++) {
                var task = tasks.get(i);
                try {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0 && !task.isDone()) throw new TimeoutException();
                    found.addAll(task.get(Math.max(1, remaining), TimeUnit.NANOSECONDS));
                    successes++;
                } catch (ExecutionException | TimeoutException | CancellationException ex) {
                    // Provider errors may contain credentials or response bodies; never log their messages.
                    log.warn("Provider search failed: provider={} category={}", active.get(i).provider(), ex.getClass().getSimpleName());
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            tasks.stream().filter(t -> !t.isDone()).forEach(t -> t.cancel(true));
        }
        return new SearchResult(aggregator.aggregate(found, query), successes > 0 && successes < active.size(), successes == 0);
    }

    private List<ProviderRelease> cached(ReleaseProvider provider, ReleaseSearchQuery query) {
        return cache.get(new CacheKey(provider.provider(), query.query(), query.limit()), key -> {
            var state = states.computeIfAbsent(provider.provider(), ignored -> new ProviderState());
            long ticket = state.acquire();
            if (ticket < 0) throw new ProviderUnavailableException(null);
            try {
                var result = List.copyOf(provider.search(query));
                state.success(ticket);
                return result;
            } catch (RuntimeException ex) {
                state.failure(ticket, ex);
                throw ex;
            }
        });
    }

    private final class ProviderState {
        private int failures;
        private long openUntil;
        private long epoch;
        private boolean probing;

        synchronized long acquire() {
            if (System.nanoTime() < openUntil || probing) return -1;
            if (openUntil != 0) probing = true;
            return epoch;
        }

        synchronized void success(long ticket) {
            if (ticket != epoch) return;
            failures = 0;
            openUntil = 0;
            probing = false;
        }

        synchronized void failure(long ticket, RuntimeException ex) {
            if (ex instanceof ProviderUnavailableException unavailable && unavailable.retryAfter() != null) {
                openUntil = Math.max(openUntil, System.nanoTime() + unavailable.retryAfter().toNanos());
                probing = false;
                epoch++;
            } else if (ticket == epoch) {
                boolean probeFailed = probing;
                probing = false;
                if (++failures >= 3 || probeFailed) {
                    openUntil = System.nanoTime() + cooldown.toNanos();
                    epoch++;
                }
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
