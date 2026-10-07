package ru.yofujitsu.lorefm.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.yofujitsu.lorefm.provider.soundcloud.*;
import ru.yofujitsu.lorefm.provider.youtube.*;
import ru.yofujitsu.lorefm.release.application.*;
import ru.yofujitsu.lorefm.release.domain.*;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class ExtraCatalogProvidersTest {
    static ReleaseSearchQuery query() { return new ReleaseSearchQuery("Björk & Æ", 10, Set.of()); }
    static void json(HttpExchange e, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json"); e.sendResponseHeaders(status, bytes.length);
        try (var out = e.getResponseBody()) { out.write(bytes); }
    }
    static final String VIDEO = """
        {"items":[{"id":{"kind":"youtube#video","videoId":"abcdefghijk"},"snippet":{"title":"Björk &amp; Æ","channelTitle":"Official channel","publishedAt":"2024-01-01T00:00:00Z","thumbnails":{"high":{"url":"https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg","width":480,"height":360}}}},
        {"id":{"videoId":"../../unsafe"},"snippet":{"title":"Bad","channelTitle":"Bad"}},null]}
        """;
    static final String PLAYLISTS = """
        {"collection":[{"urn":"soundcloud:playlists:123","title":"Record","user":{"username":"Uploader"},"is_album":true,"release_year":2020,"permalink_url":"https://soundcloud.com/uploader/sets/record","artwork_url":"https://i1.sndcdn.com/cover-large.jpg"},
        {"id":456,"title":"A set","user":{"username":"Curator"},"created_at":"2024-01-01","permalink_url":"https://soundcloud.com/curator/sets/mix","artwork_url":"javascript:unsafe"},
        {"urn":"invalid","title":"Bad"},null]}
        """;

    @Test void mapsYouTubeVideosAndKeepsApiKeyOutOfUrls() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var key = new AtomicReference<String>(); var params = new AtomicReference<String>();
        server.createContext("/search", e -> { key.set(e.getRequestHeaders().getFirst("X-Goog-Api-Key")); params.set(URLDecoder.decode(e.getRequestURI().getRawQuery(), StandardCharsets.UTF_8)); json(e, 200, VIDEO); });
        server.start();
        try {
            var properties = youtube(server); var provider = new YouTubeProvider(properties);
            var found = provider.search(query());
            assertThat(found).hasSize(1); var release = found.getFirst().release();
            assertThat(release.id().value()).isEqualTo("youtube:abcdefghijk");
            assertThat(release.title()).isEqualTo("Björk & Æ");
            assertThat(release.type()).isEqualTo(ReleaseType.VIDEO_RELEASE);
            assertThat(release.releaseYear()).isNull();
            assertThat(release.artists()).containsExactly(new Artist("Official channel"));
            assertThat(release.artwork().width()).isEqualTo(480);
            assertThat(release.sources().getFirst().externalUrl()).isEqualTo("https://www.youtube.com/watch?v=abcdefghijk");
            assertThat(key.get()).isEqualTo("test-key");
            assertThat(params.get()).contains("q=Björk & Æ", "type=video", "maxResults=10").doesNotContain("test-key");
        } finally { server.stop(0); }
    }

    @Test void mapsSoundCloudSetsAndReusesApplicationToken() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var tokens = new AtomicInteger(); var auth = new AtomicReference<String>(); var params = new AtomicReference<String>();
        server.createContext("/token", e -> { tokens.incrementAndGet(); assertThat(e.getRequestHeaders().getFirst("Authorization")).startsWith("Basic "); assertThat(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("grant_type=client_credentials"); json(e, 200, "{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":3600}"); });
        server.createContext("/playlists", e -> { auth.set(e.getRequestHeaders().getFirst("Authorization")); params.set(e.getRequestURI().getRawQuery()); json(e, 200, PLAYLISTS); });
        server.start();
        try {
            var provider = new SoundCloudProvider(soundcloud(server)); var found = provider.search(query()); provider.search(query());
            assertThat(tokens.get()).isEqualTo(1); assertThat(auth.get()).isEqualTo("OAuth access");
            assertThat(params.get()).contains("show_tracks=false", "linked_partitioning=true").doesNotContain("secret");
            assertThat(found).hasSize(2);
            assertThat(found.getFirst().release().id().value()).isEqualTo("soundcloud:123");
            assertThat(found.getFirst().release().type()).isEqualTo(ReleaseType.ALBUM);
            assertThat(found.getFirst().release().releaseYear()).isEqualTo(2020);
            assertThat(found.get(1).release().type()).isEqualTo(ReleaseType.PLAYLIST);
            assertThat(found.get(1).release().releaseYear()).isNull();
            assertThat(found.get(1).release().artwork()).isNull();
        } finally { server.stop(0); }
    }

    @Test void honorsCooldownAndRejectsBrokenResponsesWithoutExposingBodies() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", e -> { e.getResponseHeaders().set("Retry-After", "12"); json(e, 429, "private-secret"); }); server.start();
        try {
            assertThatThrownBy(() -> new YouTubeProvider(youtube(server)).search(query()))
                .isInstanceOfSatisfying(ProviderUnavailableException.class, ex -> { assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(12)); assertThat(ex.getMessage()).doesNotContain("private-secret"); });
        } finally { server.stop(0); }
    }

    @Test void soundCloudRefreshesUnauthorizedTokensAndRotatesRefreshTokens() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new java.util.ArrayList<String>(); var authorization = new AtomicReference<String>();
        var rejected = new AtomicInteger();
        server.createContext("/token", e -> {
            requests.add(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int n = requests.size();
            json(e, 200, "{\"access_token\":\"access" + n + "\",\"refresh_token\":\"refresh" + n + "\",\"expires_in\":3600}");
        });
        server.createContext("/playlists", e -> {
            authorization.set(e.getRequestHeaders().getFirst("Authorization"));
            json(e, rejected.incrementAndGet() <= 2 ? 401 : 200, PLAYLISTS);
        }); server.start();
        try {
            var provider = new SoundCloudProvider(soundcloud(server));
            assertThatThrownBy(() -> provider.search(query())).isInstanceOf(ProviderUnavailableException.class);
            assertThatThrownBy(() -> provider.search(query())).isInstanceOf(ProviderUnavailableException.class);
            assertThat(provider.search(query())).hasSize(2);
            assertThat(requests).containsExactly("grant_type=client_credentials",
                "grant_type=refresh_token&client_id=client&client_secret=secret&refresh_token=refresh1",
                "grant_type=refresh_token&client_id=client&client_secret=secret&refresh_token=refresh2");
            assertThat(authorization.get()).isEqualTo("OAuth access3");
        } finally { server.stop(0); }
    }

    @Test void normalSearchNeverCallsYouTubeAndExplicitSearchUsesExistingCache() {
        var calls = new AtomicInteger();
        var soundcloudCalls = new AtomicInteger();
        var soundcloud = new ReleaseProvider() { public Provider provider() { return Provider.SOUNDCLOUD; } public List<ProviderRelease> search(ReleaseSearchQuery q) { soundcloudCalls.incrementAndGet(); return List.of(); } };
        var youtube = new ReleaseProvider() { public Provider provider() { return Provider.YOUTUBE; } public List<ProviderRelease> search(ReleaseSearchQuery q) { calls.incrementAndGet(); return List.of(); } };
        try (var service = new ReleaseSearchService(List.of(youtube, soundcloud), Duration.ofMinutes(15), 100, Duration.ofSeconds(2), Duration.ofSeconds(30))) {
            service.search(query()); assertThat(calls.get()).isZero();
            assertThat(soundcloudCalls.get()).isZero();
            var explicit = new ReleaseSearchQuery(query().query(), 10, Set.of(Provider.YOUTUBE));
            service.search(explicit); service.search(explicit); assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test void disabledOrUnconfiguredProvidersMakeNoNetworkRequests() {
        assertThat(new YouTubeProvider(new YouTubeProperties()).enabled()).isFalse();
        assertThat(new SoundCloudProvider(new SoundCloudProperties()).enabled()).isFalse();
        assertThat(new YouTubeProvider(new YouTubeProperties()).search(query())).isEmpty();
        assertThat(new SoundCloudProvider(new SoundCloudProperties()).search(query())).isEmpty();
    }

    @Test void boundsTotalRequestTimeIncludingSlowHttpResponses() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(executor);
            server.createContext("/search", e -> { try { Thread.sleep(500); json(e, 200, VIDEO); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }); server.start();
            try {
                var p = youtube(server); p.setRequestTimeout(Duration.ofMillis(150));
                var provider = new YouTubeProvider(p);
                long started = System.nanoTime();
                assertThatThrownBy(() -> provider.search(query())).isInstanceOf(ProviderUnavailableException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(400));
            } finally { server.stop(0); }
        }
    }
    static String base(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    static YouTubeProperties youtube(HttpServer server) { var p = new YouTubeProperties(); p.setEnabled(true); p.setApiKey("test-key"); p.setApiBaseUrl(base(server)); return p; }
    static SoundCloudProperties soundcloud(HttpServer server) { var p = new SoundCloudProperties(); p.setEnabled(true); p.setClientId("client"); p.setClientSecret("secret"); p.setApiBaseUrl(base(server)); p.setTokenUrl(base(server) + "/token"); return p; }
}
