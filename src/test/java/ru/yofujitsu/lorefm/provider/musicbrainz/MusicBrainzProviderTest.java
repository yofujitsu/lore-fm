package ru.yofujitsu.lorefm.provider.musicbrainz;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
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

class MusicBrainzProviderTest {
    static final String ID = "48140466-cff6-3222-bd55-63c27e43190d";
    static final String JSON = """
        {"release-groups":[
          {"id":"48140466-cff6-3222-bd55-63c27e43190d","title":"Cold Visions","score":98,
           "primary-type":"Album","first-release-date":"2024-04-24",
           "artist-credit":[{"name":"Bladee","artist":{"name":"Bladee"}}]},
          {"id":"javascript:bad","title":"Unsafe","artist-credit":[{"name":"Bladee"}]},
          {"id":"c31a5e2b-0bf8-32e0-8aeb-ef4ba9973932","title":"Björk\\u0000 & Æ","score":"90",
           "primary-type":"EP","first-release-date":"2024-02-31","artist-credit":[{"artist":{"name":"Björk"}}]},
          {"id":"1-1-1-1-1","title":"Short UUID","artist-credit":[{"name":"Bladee"}]},
          {"id":"76df3287-6cda-33eb-8e9a-044b5e15ffdd","title":"Mixtape","primary-type":"Album",
           "secondary-types":["Mixtape/Street"],"artist-credit":[{"name":"Bladee"}]},
          {"title":{},"artist-credit":"bad"}, null
        ]}
        """;
    static ReleaseSearchQuery query(String text) { return new ReleaseSearchQuery(text, 10, Set.of()); }

    static void json(HttpExchange e, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        try (var out = e.getResponseBody()) { out.write(bytes); }
    }

    @Test void mapsValidNeighborsAndEscapesLiteralQueries() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            var userAgent = new AtomicReference<String>(); var request = new AtomicReference<String>();
            server.createContext("/ws/2/release-group", e -> {
                userAgent.set(e.getRequestHeaders().getFirst("User-Agent"));
                request.set(URLDecoder.decode(e.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
                json(e, 200, JSON);
            });
            server.start();
            try {
                var p = properties(server);
                var found = new MusicBrainzProvider(p).search(query("Björk & \"Æ\""));
                assertThat(found).hasSize(3);
                var release = found.getFirst().release();
                assertThat(release.id().value()).isEqualTo("musicbrainz:" + ID);
                assertThat(release.artists()).containsExactly(new Artist("Bladee"));
                assertThat(release.releaseYear()).isEqualTo(2024);
                assertThat(release.type()).isEqualTo(ReleaseType.ALBUM);
                assertThat(found.getFirst().confidence()).isEqualTo(98);
                assertThat(release.artwork().url()).isEqualTo("https://coverartarchive.org/release-group/" + ID + "/front-500");
                assertThat(release.sources().getFirst().externalUrl()).isEqualTo("https://musicbrainz.org/release-group/" + ID);
                assertThat(found.get(1).release().title()).isEqualTo("Björk & Æ");
                assertThat(found.get(1).release().releaseYear()).isNull();
                assertThat(found.get(1).release().type()).isEqualTo(ReleaseType.EP);
                assertThat(found.get(2).release().type()).isEqualTo(ReleaseType.MIXTAPE);
                assertThat(userAgent.get()).isEqualTo("Ninefold/test (mailto:developer@example.com)");
                assertThat(request.get()).contains("fmt=json", "limit=10", "releasegroup:\"Björk\"", "artist:\"Björk\"", "\\\"Æ\\\"");
                p.setEnabled(false);
                assertThat(new MusicBrainzProvider(p).enabled()).isFalse();
            } finally { server.stop(0); }
        }
    }

    @Test void spacesOutboundRequestsAndHonorsRetryAfterWithoutRetrying() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            var starts = new java.util.concurrent.CopyOnWriteArrayList<Long>();
            server.createContext("/ws/2/release-group", e -> {
                starts.add(System.nanoTime());
                if (starts.size() == 2) { e.getResponseHeaders().set("Retry-After", "3"); json(e, 429, "private-provider-secret"); }
                else json(e, 200, JSON);
            });
            server.start();
            try {
                var provider = new MusicBrainzProvider(properties(server));
                provider.search(query("Cold Visions"));
                assertThatThrownBy(() -> provider.search(query("Bladee"))).isInstanceOf(ProviderUnavailableException.class)
                        .hasMessageNotContaining("private-provider-secret");
                assertThat(starts).hasSize(2);
                assertThat(Duration.ofNanos(starts.get(1) - starts.getFirst())).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
                assertThatThrownBy(() -> provider.search(query("Björk"))).isInstanceOf(ProviderUnavailableException.class);
                assertThat(starts).hasSize(2);
            } finally { server.stop(0); }
        }
    }

    @Test void boundsNetworkAndRateWaitAndSkipsDisabledProvider() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            var calls = new AtomicInteger();
            server.createContext("/ws/2/release-group", e -> {
                calls.incrementAndGet();
                try { Thread.sleep(800); json(e, 200, JSON); } catch (Exception ignored) { e.close(); }
            });
            server.start();
            try {
                var p = properties(server); p.setRequestTimeout(Duration.ofMillis(150));
                var provider = new MusicBrainzProvider(p);
                long start = System.nanoTime();
                assertThatThrownBy(() -> provider.search(query("timeout"))).isInstanceOf(RuntimeException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(700));
                assertThatThrownBy(() -> provider.search(query("rate wait"))).isInstanceOf(ProviderUnavailableException.class);
                assertThat(calls.get()).isEqualTo(1);
                p.setEnabled(false);
                assertThat(provider.search(query("disabled"))).isEmpty();
                assertThat(calls.get()).isEqualTo(1);
            } finally { server.stop(0); }
        }
    }

    @Test void coldRequestPreparationConsumesProviderBudget() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/ws/2/release-group", e -> {
                try { Thread.sleep(1000); json(e, 200, JSON); }
                catch (Exception ignored) { e.close(); }
            });
            server.start();
            try {
                var provider = new MusicBrainzProvider(properties(server));
                long start = System.nanoTime();
                // A warmed client may finish successfully; either outcome must honor the total budget.
                try { provider.search(query("cold start")); } catch (RuntimeException ignored) { }
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1300));
            } finally { server.stop(0); }
        }
    }

    @Test void queuedSearchRechecksCooldownAfterAnotherRequestIsThrottled() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            var entered = new java.util.concurrent.CountDownLatch(1);
            var reply = new java.util.concurrent.CountDownLatch(1);
            var calls = new AtomicInteger();
            server.createContext("/ws/2/release-group", e -> {
                calls.incrementAndGet(); entered.countDown();
                try { reply.await(2, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                e.getResponseHeaders().set("Retry-After", "5"); json(e, 503, "unavailable");
            });
            server.start();
            try {
                var provider = new MusicBrainzProvider(properties(server));
                var first = executor.submit(() -> provider.search(query("first")));
                assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> provider.search(query("second")));
                Thread.sleep(100); reply.countDown();
                assertThatThrownBy(() -> first.get()).hasCauseInstanceOf(ProviderUnavailableException.class);
                assertThatThrownBy(() -> second.get()).hasCauseInstanceOf(ProviderUnavailableException.class);
                assertThat(calls.get()).isEqualTo(1);
            } finally { reply.countDown(); server.stop(0); }
        }
    }

    static MusicBrainzProperties properties(HttpServer server) {
        var p = new MusicBrainzProperties();
        p.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/ws/2");
        p.setUserAgent("Ninefold/test (mailto:developer@example.com)");
        return p;
    }
}
