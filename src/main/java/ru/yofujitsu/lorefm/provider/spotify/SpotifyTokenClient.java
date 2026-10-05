package ru.yofujitsu.lorefm.provider.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SpotifyTokenClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String token, @JsonProperty("expires_in") long expiresIn) {}
    private record CachedToken(String value, long refreshAt) {}
    private final SpotifyProperties properties;
    private final SpotifyHttp http;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile CachedToken cached;

    public SpotifyTokenClient(SpotifyProperties properties) {
        this.properties = properties;
        this.http = new SpotifyHttp(properties.getRequestTimeout());
    }

    public String accessToken(long deadlineNanos) {
        SpotifyHttp.remaining(deadlineNanos);
        var token = cached;
        if (token != null && System.nanoTime() < token.refreshAt()) return token.value();
        boolean acquired = false;
        try {
            acquired = lock.tryLock(SpotifyHttp.remaining(deadlineNanos), TimeUnit.NANOSECONDS);
            if (!acquired) throw new IllegalStateException("Spotify token request timed out");
            token = cached;
            if (token != null && System.nanoTime() < token.refreshAt()) return token.value();
            var response = http.client(deadlineNanos).post().uri(properties.getTokenUrl())
                    .headers(headers -> headers.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials")
                    .retrieve().body(TokenResponse.class);
            if (response == null || response.token() == null || response.token().isBlank()
                    || response.expiresIn() <= 0 || response.expiresIn() > 86400) {
                throw new IllegalStateException("Invalid Spotify token response");
            }
            long safetySeconds = Math.min(30, response.expiresIn() / 10);
            cached = new CachedToken(response.token(), System.nanoTime() + Duration.ofSeconds(response.expiresIn() - safetySeconds).toNanos());
            return cached.value();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Spotify token request interrupted");
        } finally {
            if (acquired) lock.unlock();
        }
    }

    public void invalidateToken() { cached = null; }
}
