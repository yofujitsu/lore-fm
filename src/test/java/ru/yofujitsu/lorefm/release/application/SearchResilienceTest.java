package ru.yofujitsu.lorefm.release.application;

import org.junit.jupiter.api.Test;
import ru.yofujitsu.lorefm.release.domain.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static ru.yofujitsu.lorefm.release.application.ReleaseSearchServiceTest.provider;
import static ru.yofujitsu.lorefm.release.application.ReleaseSearchServiceTest.release;

class SearchResilienceTest {
    static ReleaseSearchQuery query(String text) { return new ReleaseSearchQuery(text, 10, Set.of()); }
    static ReleaseSearchService service(List<ReleaseProvider> sources, Duration timeout, Duration cooldown) {
        return new ReleaseSearchService(sources, Duration.ofMinutes(15), 1000, timeout, cooldown);
    }

    @Test void startsProvidersConcurrentlyAndKeepsHealthyResultsWhenOneStalls() throws Exception {
        var entered = new CountDownLatch(2); var cancelled = new CountDownLatch(1);
        var good = provider(Provider.SPOTIFY, q -> {
            entered.countDown();
            try { if (!entered.await(1, TimeUnit.SECONDS)) throw new IllegalStateException("Provider calls were sequential"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            return List.of(new ProviderRelease(release()));
        });
        var stalled = provider(Provider.MUSICBRAINZ, q -> {
            entered.countDown();
            try { Thread.sleep(5000); }
            catch (InterruptedException ex) { cancelled.countDown(); Thread.currentThread().interrupt(); throw new IllegalStateException("cancelled"); }
            return List.of();
        });
        try (var search = service(List.of(stalled, good), Duration.ofMillis(200), Duration.ofSeconds(30))) {
            long start = System.nanoTime();
            var result = search.search(query("Cold Visions"));
            assertThat(result.results()).containsExactly(release());
            assertThat(result.partial()).isTrue(); assertThat(result.unavailable()).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(800));
            assertThat(cancelled.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void distinguishesHealthyEmptyFromUnavailableAndSkipsDisabledAdapters() {
        var empty = provider(Provider.MUSICBRAINZ, q -> List.of());
        var bad = provider(Provider.SPOTIFY, q -> { throw new IllegalStateException("private-provider-secret"); });
        var disabled = new ReleaseProvider() {
            public Provider provider() { return Provider.YOUTUBE; }
            public boolean enabled() { return false; }
            public List<ProviderRelease> search(ReleaseSearchQuery q) { throw new AssertionError("Disabled adapter invoked"); }
        };
        try (var search = service(List.of(empty, disabled), Duration.ofSeconds(2), Duration.ofSeconds(30))) {
            var result = search.search(query("empty"));
            assertThat(result.results()).isEmpty(); assertThat(result.partial()).isFalse(); assertThat(result.unavailable()).isFalse();
        }
        try (var search = service(List.of(bad), Duration.ofSeconds(2), Duration.ofSeconds(30))) {
            assertThat(search.search(query("bad")).unavailable()).isTrue();
        }
        try (var search = service(List.of(empty, bad), Duration.ofSeconds(2), Duration.ofSeconds(30))) {
            assertThat(search.search(query("mixed")).partial()).isTrue();
        }
    }

    @Test void coalescesConcurrentSameKeyRequestsAndPreservesCacheThroughProviderCooldown() throws Exception {
        var calls = new AtomicInteger(); var entered = new CountDownLatch(1); var reply = new CountDownLatch(1);
        var source = provider(Provider.MUSICBRAINZ, q -> {
            calls.incrementAndGet(); entered.countDown();
            if (q.query().startsWith("fail")) throw new ProviderUnavailableException(Duration.ofSeconds(10));
            try { reply.await(1, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            return List.of(new ProviderRelease(release()));
        });
        try (var search = service(List.of(source), Duration.ofSeconds(2), Duration.ofSeconds(30));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> search.search(query("cached")));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> search.search(query("cached")));
            reply.countDown();
            assertThat(first.get().results()).containsExactly(release());
            assertThat(second.get().results()).containsExactly(release());
            assertThat(calls.get()).isEqualTo(1);
            assertThat(search.search(query("fail now")).unavailable()).isTrue();
            assertThat(search.search(query("different")).unavailable()).isTrue();
            assertThat(search.search(query("cached")).results()).containsExactly(release());
            assertThat(calls.get()).isEqualTo(2);
        } finally { reply.countDown(); }
    }

    @Test void opensCircuitAfterRepeatedFailuresAndAllowsOnlyOneRecoveryProbe() throws Exception {
        var calls = new AtomicInteger(); var probeEntered = new CountDownLatch(1); var reply = new CountDownLatch(1);
        var source = provider(Provider.SPOTIFY, q -> {
            int call = calls.incrementAndGet();
            if (call <= 3) throw new IllegalStateException("unavailable");
            if (call == 4) {
                probeEntered.countDown();
                try { reply.await(1, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            return List.of(new ProviderRelease(release()));
        });
        try (var search = service(List.of(source), Duration.ofSeconds(2), Duration.ofMillis(150));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 3; i++) assertThat(search.search(query("failure " + i)).unavailable()).isTrue();
            assertThat(search.search(query("open circuit")).unavailable()).isTrue(); assertThat(calls.get()).isEqualTo(3);
            Thread.sleep(180);
            var probe = executor.submit(() -> search.search(query("probe")));
            assertThat(probeEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(search.search(query("second probe")).unavailable()).isTrue(); assertThat(calls.get()).isEqualTo(4);
            reply.countDown();
            assertThat(probe.get().results()).containsExactly(release());
            assertThat(search.search(query("recovered")).results()).containsExactly(release());
            assertThat(calls.get()).isEqualTo(5);
        } finally { reply.countDown(); }
    }
}
