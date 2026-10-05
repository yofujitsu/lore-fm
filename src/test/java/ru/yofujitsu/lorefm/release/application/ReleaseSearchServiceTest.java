package ru.yofujitsu.lorefm.release.application;

import org.junit.jupiter.api.Test;
import ru.yofujitsu.lorefm.release.domain.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class ReleaseSearchServiceTest {
    static Release release() {
        return new Release(new ReleaseId("spotify:123"), "Cold Visions", List.of(new Artist("Bladee")),
                2024, ReleaseType.ALBUM, null, List.of(new ReleaseSource(Provider.SPOTIFY, "123", "https://open.spotify.com/album/123")));
    }

    static ReleaseProvider provider(Provider name, java.util.function.Function<ReleaseSearchQuery, List<ProviderRelease>> search) {
        return new ReleaseProvider() {
            public Provider provider() { return name; }
            public List<ProviderRelease> search(ReleaseSearchQuery query) { return search.apply(query); }
        };
    }

    @Test
    void isolatesFailureAndHonorsProviderFilter() {
        var good = provider(Provider.SPOTIFY, q -> List.of(new ProviderRelease(release())));
        var bad = provider(Provider.MUSICBRAINZ, q -> { throw new IllegalStateException("private-provider-secret"); });
        var service = new ReleaseSearchService(List.of(bad, good), Duration.ofMinutes(15), 1000);
        assertThat(service.search(new ReleaseSearchQuery("Cold Visions", 10, Set.of()))).containsExactly(release());
        assertThat(service.search(new ReleaseSearchQuery("Cold Visions", 10, Set.of(Provider.MUSICBRAINZ)))).isEmpty();
        assertThat(new ReleaseSearchService(List.of(), Duration.ofMinutes(15), 1000)
                .search(new ReleaseSearchQuery("test", 10, Set.of()))).isEmpty();
    }

    @Test
    void cachesSuccessButNotFailureAndIncludesQueryAndLimitInKey() {
        var calls = new AtomicInteger();
        var source = provider(Provider.SPOTIFY, q -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("failure");
            return List.of(new ProviderRelease(release()));
        });
        var service = new ReleaseSearchService(List.of(source), Duration.ofMinutes(15), 1000);
        var query = new ReleaseSearchQuery("Cold Visions", 10, Set.of());
        assertThat(service.search(query)).isEmpty();
        assertThat(service.search(query)).containsExactly(release());
        assertThat(service.search(query)).containsExactly(release());
        assertThat(calls.get()).isEqualTo(2);
        service.search(new ReleaseSearchQuery("Bladee", 10, Set.of()));
        service.search(new ReleaseSearchQuery("Cold Visions", 20, Set.of()));
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void validatesUnicodeLengthWithoutChangingDisplayQuery() {
        assertThat(new ReleaseSearchQuery("  Björk & Æ  ", 10, Set.of()).query()).isEqualTo("Björk & Æ");
        assertThat(new ReleaseSearchQuery("🎵🎶", 20, Set.of()).query()).isEqualTo("🎵🎶");
        for (String q : List.of("", " ", "a", "🎵", "x".repeat(201))) {
            assertThatThrownBy(() -> new ReleaseSearchQuery(q, 10, Set.of())).isInstanceOf(InvalidSearchException.class);
        }
        assertThatThrownBy(() -> new ReleaseSearchQuery("ab", 0, Set.of())).isInstanceOf(InvalidSearchException.class);
        assertThatThrownBy(() -> new ReleaseSearchQuery("ab", 21, Set.of())).isInstanceOf(InvalidSearchException.class);
    }
}
