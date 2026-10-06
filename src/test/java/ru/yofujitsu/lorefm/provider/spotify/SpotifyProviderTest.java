package ru.yofujitsu.lorefm.provider.spotify;

import org.junit.jupiter.api.Test;
import ru.yofujitsu.lorefm.release.application.ReleaseSearchQuery;
import ru.yofujitsu.lorefm.release.domain.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SpotifyProviderTest {
    @Test
    void forwardsRetryAfterWithoutExposingProviderBody() throws Exception {
        try (var server = new SpotifyMockServer()) {
            server.server.createContext("/api/token", e -> SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}"));
            server.server.createContext("/v1/search", e -> {
                e.getResponseHeaders().set("Retry-After", "12");
                SpotifyMockServer.json(e, 429, "{\"error\":\"private-provider-secret\"}");
            });
            var p = server.properties();
            assertThatThrownBy(() -> new SpotifyProvider(p, new SpotifyTokenClient(p)).search(new ReleaseSearchQuery("test", 10, Set.of())))
                    .isInstanceOf(ru.yofujitsu.lorefm.release.application.ProviderUnavailableException.class)
                    .satisfies(ex -> assertThat(((ru.yofujitsu.lorefm.release.application.ProviderUnavailableException) ex).retryAfter()).isEqualTo(Duration.ofSeconds(12)))
                    .hasMessageNotContaining("private-provider-secret");
        }
    }

    @Test
    void unauthorizedSearchRefreshesTokenOnNextAttempt() throws Exception {
        try (var server = new SpotifyMockServer()) {
            var tokenCalls = new AtomicInteger();
            var searchCalls = new AtomicInteger();
            server.server.createContext("/api/token", e -> {
                tokenCalls.incrementAndGet();
                SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}");
            });
            server.server.createContext("/v1/search", e -> {
                if (searchCalls.incrementAndGet() == 1) SpotifyMockServer.json(e, 401, "{\"error\":\"expired\"}");
                else SpotifyMockServer.json(e, 200, "{\"albums\":{\"items\":[]}}");
            });
            var p = server.properties();
            var provider = new SpotifyProvider(p, new SpotifyTokenClient(p));
            var query = new ReleaseSearchQuery("test", 10, Set.of());
            assertThatThrownBy(() -> provider.search(query)).isInstanceOf(RuntimeException.class);
            assertThat(provider.search(query)).isEmpty();
            assertThat(tokenCalls.get()).isEqualTo(2);
        }
    }

    @Test
    void structurallyMalformedItemDoesNotDiscardValidNeighbors() throws Exception {
        try (var server = new SpotifyMockServer()) {
            server.server.createContext("/api/token", e -> SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}"));
            server.server.createContext("/v1/search", e -> SpotifyMockServer.json(e, 200, """
                    {"albums":{"items":[
                      {"id":"good","name":"Good release","artists":[{"name":"Artist"}]},
                      {"id":"bad","name":"Broken artists","artists":{}},
                      {"id":"badimage","name":"Broken image","artists":[{"name":"Artist"}],"images":[{"width":{}}]},
                      {"id":"other","name":"Other release","artists":[{"name":"Artist"}]}
                    ]}}
                    """));
            var p = server.properties();
            var results = new SpotifyProvider(p, new SpotifyTokenClient(p)).search(new ReleaseSearchQuery("test", 10, Set.of()));
            assertThat(results).extracting(r -> r.release().title()).containsExactly("Good release", "Other release");
        }
    }

    @Test
    void invalidCalendarDatesHaveUnknownYear() throws Exception {
        try (var server = new SpotifyMockServer()) {
            server.server.createContext("/api/token", e -> SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}"));
            server.server.createContext("/v1/search", e -> SpotifyMockServer.json(e, 200, """
                    {"albums":{"items":[
                      {"id":"a","name":"Bad month","release_date":"2024-99-99","artists":[{"name":"Artist"}]},
                      {"id":"b","name":"Bad leap day","release_date":"2023-02-29","artists":[{"name":"Artist"}]},
                      {"id":"c","name":"Valid leap day","release_date":"2024-02-29","artists":[{"name":"Artist"}]}
                    ]}}
                    """));
            var p = server.properties();
            var results = new SpotifyProvider(p, new SpotifyTokenClient(p)).search(new ReleaseSearchQuery("test", 10, Set.of()));
            assertThat(results).extracting(r -> r.release().releaseYear()).containsExactly(null, null, 2024);
        }
    }

    @Test
    void sendsEncodedSearchAndMapsProviderData() throws Exception {
        try (var server = new SpotifyMockServer()) {
            var rawQuery = new AtomicReference<String>();
            var authorization = new AtomicReference<String>();
            server.server.createContext("/api/token", e -> SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}"));
            String fixture;
            try (var input = getClass().getResourceAsStream("/spotify/search.json")) { fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            server.server.createContext("/v1/search", e -> {
                rawQuery.set(e.getRequestURI().getRawQuery()); authorization.set(e.getRequestHeaders().getFirst("Authorization"));
                SpotifyMockServer.json(e, 200, fixture);
            });
            var p = server.properties();
            var provider = new SpotifyProvider(p, new SpotifyTokenClient(p));
            var results = provider.search(new ReleaseSearchQuery("Björk & Æ", 20, Set.of()));
            assertThat(rawQuery.get()).contains("type=album", "market=US", "limit=10", "q=Bj%C3%B6rk%20%26%20%C3%86");
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
            assertThat(results).hasSize(4);
            var first = results.getFirst().release();
            assertThat(first.id().value()).isEqualTo("spotify:123");
            assertThat(first.title()).isEqualTo("Cold Visions");
            assertThat(first.artists()).extracting(Artist::name).containsExactly("Bladee", "Yung Lean");
            assertThat(first.releaseYear()).isEqualTo(2024);
            assertThat(first.artwork().url()).isEqualTo("https://i.scdn.co/image/large");
            assertThat(first.sources().getFirst().externalUrl()).isEqualTo("https://open.spotify.com/album/123");
            assertThat(first.type()).isEqualTo(ReleaseType.ALBUM);
            assertThat(results.get(1).release().artwork()).isNull();
            assertThat(results.get(1).release().releaseYear()).isNull();
            assertThat(results.get(1).release().type()).isEqualTo(ReleaseType.UNKNOWN);
            assertThat(results.get(2).release().releaseYear()).isEqualTo(1999);
            assertThat(results.get(2).release().type()).isEqualTo(ReleaseType.SINGLE);
            assertThat(results.get(3).release().type()).isEqualTo(ReleaseType.COMPILATION);
        }
    }

    @Test
    void disabledOrUnconfiguredProviderNeverRequestsToken() throws Exception {
        try (var server = new SpotifyMockServer()) {
            var p = server.properties();
            p.setEnabled(false);
            assertThat(new SpotifyProvider(p, new SpotifyTokenClient(p)).search(new ReleaseSearchQuery("test", 10, Set.of()))).isEmpty();
            p.setEnabled(true); p.setClientSecret("");
            assertThat(new SpotifyProvider(p, new SpotifyTokenClient(p)).search(new ReleaseSearchQuery("test", 10, Set.of()))).isEmpty();
        }
    }

    @Test
    void errorResponseDoesNotLeakProviderBody() throws Exception {
        for (int status : new int[] {401, 403, 429, 500}) {
            try (var server = new SpotifyMockServer()) {
                server.server.createContext("/api/token", e -> SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}"));
                server.server.createContext("/v1/search", e -> SpotifyMockServer.json(e, status, "{\"error\":\"private-provider-secret\"}"));
                var p = server.properties();
                assertThatThrownBy(() -> new SpotifyProvider(p, new SpotifyTokenClient(p)).search(new ReleaseSearchQuery("test", 10, Set.of())))
                        .isInstanceOf(RuntimeException.class).hasMessageNotContaining("private-provider-secret")
                        .hasMessageNotContaining("test-token").hasMessageNotContaining("test-secret");
            }
        }
    }

    @Test
    void tokenAndSearchShareOneRequestBudget() throws Exception {
        try (var server = new SpotifyMockServer()) {
            server.server.createContext("/api/token", e -> {
                try { Thread.sleep(150); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}");
            });
            server.server.createContext("/v1/search", e -> {
                try { Thread.sleep(800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                SpotifyMockServer.json(e, 200, "{\"albums\":{\"items\":[]}}");
            });
            var p = server.properties(); p.setRequestTimeout(Duration.ofMillis(350));
            long started = System.nanoTime();
            assertThatThrownBy(() -> new SpotifyProvider(p, new SpotifyTokenClient(p)).search(new ReleaseSearchQuery("test", 10, Set.of())))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(800));
        }
    }
}
