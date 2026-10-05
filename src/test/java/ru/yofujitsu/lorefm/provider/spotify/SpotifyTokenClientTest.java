package ru.yofujitsu.lorefm.provider.spotify;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class SpotifyTokenClientTest {
    static long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(3); }

    @Test
    void cachesTokenAndSendsClientCredentials() throws Exception {
        try (var server = new SpotifyMockServer()) {
            var calls = new AtomicInteger(); var auth = new AtomicReference<String>(); var body = new AtomicReference<String>();
            server.server.createContext("/api/token", e -> {
                calls.incrementAndGet(); auth.set(e.getRequestHeaders().getFirst("Authorization"));
                body.set(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}");
            });
            var client = new SpotifyTokenClient(server.properties());
            assertThat(client.accessToken(deadline())).isEqualTo("test-token");
            assertThat(client.accessToken(deadline())).isEqualTo("test-token");
            assertThat(calls.get()).isEqualTo(1);
            assertThat(auth.get()).isEqualTo("Basic " + Base64.getEncoder().encodeToString("test-id:test-secret".getBytes(StandardCharsets.UTF_8)));
            assertThat(body.get()).isEqualTo("grant_type=client_credentials");
            client.invalidateToken();
            assertThat(client.accessToken(deadline())).isEqualTo("test-token");
            assertThat(calls.get()).isEqualTo(2);
        }
    }

    @Test
    void refreshesExpiredToken() throws Exception {
        try (var server = new SpotifyMockServer()) {
            var calls = new AtomicInteger();
            server.server.createContext("/api/token", e -> {
                int count = calls.incrementAndGet();
                SpotifyMockServer.json(e, 200, "{\"access_token\":\"token-" + count + "\",\"expires_in\":1}");
            });
            var client = new SpotifyTokenClient(server.properties());
            assertThat(client.accessToken(deadline())).isEqualTo("token-1");
            Thread.sleep(1100);
            assertThat(client.accessToken(deadline())).isEqualTo("token-2");
        }
    }

    @Test
    void concurrentCallsShareTokenAndLockWaitingHasDeadline() throws Exception {
        try (var server = new SpotifyMockServer(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var calls = new AtomicInteger(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            server.server.createContext("/api/token", e -> {
                calls.incrementAndGet(); entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                SpotifyMockServer.json(e, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}");
            });
            var client = new SpotifyTokenClient(server.properties());
            var first = executor.submit(() -> client.accessToken(deadline()));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> client.accessToken(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50))).isInstanceOf(RuntimeException.class);
            } finally { release.countDown(); }
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("test-token");
            assertThat(client.accessToken(deadline())).isEqualTo("test-token");
            assertThat(calls.get()).isEqualTo(1);
        }
    }
}
