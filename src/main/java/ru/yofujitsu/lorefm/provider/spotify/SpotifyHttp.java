package ru.yofujitsu.lorefm.provider.spotify;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

final class SpotifyHttp {
    private final HttpClient client;

    SpotifyHttp(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMillis(1500)) > 0) {
            throw new IllegalArgumentException("Spotify timeout must be between 1 and 1500 ms");
        }
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static long remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new IllegalStateException("Spotify request timed out");
        return nanos;
    }

    RestClient client(long deadline) {
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofNanos(remaining(deadline)));
        return RestClient.builder().requestFactory(factory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new Failure(response.getStatusCode().value());
                }).build();
    }

    static final class Failure extends RuntimeException {
        final int status;
        Failure(int status) { super("Spotify request failed (HTTP " + status + ")"); this.status = status; }
    }
}
