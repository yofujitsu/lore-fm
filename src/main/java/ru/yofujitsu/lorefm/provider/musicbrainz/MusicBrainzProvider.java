package ru.yofujitsu.lorefm.provider.musicbrainz;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import ru.yofujitsu.lorefm.release.application.*;
import ru.yofujitsu.lorefm.release.domain.*;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class MusicBrainzProvider implements ReleaseProvider {
    private final MusicBrainzProperties properties;
    private final HttpClient http;
    private final ReentrantLock gate = new ReentrantLock(true);
    private volatile long nextStart;

    public MusicBrainzProvider(MusicBrainzProperties properties) {
        this.properties = properties;
        var timeout = properties.getRequestTimeout();
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMillis(1200)) > 0) {
            throw new IllegalArgumentException("MusicBrainz timeout must be between 1 and 1200 ms");
        }
        if (properties.getUserAgent() == null || properties.getUserAgent().isBlank() || properties.getUserAgent().matches("(?s).*\\p{Cc}.*")) {
            throw new IllegalArgumentException("MusicBrainz User-Agent must identify the application");
        }
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public Provider provider() { return Provider.MUSICBRAINZ; }
    @Override public boolean enabled() { return properties.isEnabled(); }

    @Override public List<ProviderRelease> search(ReleaseSearchQuery query) {
        if (!enabled()) return List.of();
        long deadline = System.nanoTime() + properties.getRequestTimeout().toNanos();
        var factory = new JdkClientHttpRequestFactory(http);
        var client = RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", properties.getUserAgent())
                .defaultHeader("Accept", "application/json")
                .defaultStatusHandler(status -> !status.is2xxSuccessful(), (request, response) -> {
                    Duration retry = null;
                    if (response.getStatusCode().value() == 429 || response.getStatusCode().value() == 503) {
                        retry = ProviderUnavailableException.retryAfter(response.getHeaders().getFirst("Retry-After"));
                        gate.lock();
                        try { nextStart = Math.max(nextStart, System.nanoTime() + retry.toNanos()); }
                        finally { gate.unlock(); }
                    }
                    throw new ProviderUnavailableException(retry);
                }).build();
        var uri = UriComponentsBuilder.fromUriString(properties.getApiBaseUrl() + "/release-group")
                .queryParam("query", "{query}").queryParam("fmt", "json").queryParam("limit", query.limit())
                .encode().buildAndExpand(lucene(query.query())).toUri();
        var request = client.get().uri(uri);
        awaitSlot(deadline);
        try {
            factory.setReadTimeout(Duration.ofNanos(remaining(deadline)));
            var response = request.retrieve().body(JsonNode.class);
            remaining(deadline);
            if (response == null || !response.path("release-groups").isArray()) throw new ProviderUnavailableException(null);
            var found = new ArrayList<ProviderRelease>();
            for (var group : response.path("release-groups")) {
                try { found.add(map(group)); }
                catch (IllegalArgumentException | NullPointerException ignored) { /* Keep valid neighboring items. */ }
            }
            remaining(deadline);
            return List.copyOf(found);
        } finally {
            // ponytail: serialize calls and wait a second after completion; share the limiter if replicated.
            nextStart = Math.max(nextStart, System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
            gate.unlock();
        }
    }

    private void awaitSlot(long deadline) {
        try {
            if (!gate.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) throw new ProviderUnavailableException(Duration.ofSeconds(1));
            long wait = nextStart - System.nanoTime();
            if (wait > 0) {
                if (wait >= remaining(deadline)) throw new ProviderUnavailableException(Duration.ofNanos(wait));
                Thread.sleep(Duration.ofNanos(wait));
            }
            remaining(deadline);
        } catch (InterruptedException ex) {
            if (gate.isHeldByCurrentThread()) gate.unlock();
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(null);
        } catch (RuntimeException ex) {
            if (gate.isHeldByCurrentThread()) gate.unlock();
            throw ex;
        }
    }

    private static long remaining(long deadline) {
        long time = deadline - System.nanoTime();
        if (time <= 0 || Thread.currentThread().isInterrupted()) throw new ProviderUnavailableException(null);
        return time;
    }

    private ProviderRelease map(JsonNode group) {
        String id = text(group.path("id"));
        if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("Invalid MusicBrainz ID");
        id = id.toLowerCase(Locale.ROOT);
        String title = text(group.path("title"));
        var artists = new ArrayList<Artist>();
        for (var credit : group.path("artist-credit")) {
            var name = credit.path("name");
            if (!name.isString() || name.asString().isBlank()) name = credit.path("artist").path("name");
            artists.add(new Artist(text(name)));
        }
        if (artists.isEmpty()) throw new IllegalArgumentException("Missing artist credits");
        var type = switch (group.path("primary-type").asString("")) {
            case "Album" -> ReleaseType.ALBUM;
            case "EP" -> ReleaseType.EP;
            case "Single" -> ReleaseType.SINGLE;
            default -> ReleaseType.UNKNOWN;
        };
        for (var secondary : group.path("secondary-types")) {
            if ("Compilation".equals(secondary.asString(""))) type = ReleaseType.COMPILATION;
            if ("Mixtape/Street".equals(secondary.asString(""))) type = ReleaseType.MIXTAPE;
        }
        Integer year = year(group.path("first-release-date").asString(""));
        var artwork = new Artwork("https://coverartarchive.org/release-group/" + id + "/front-500", null, null, provider());
        var source = new ReleaseSource(provider(), id, "https://musicbrainz.org/release-group/" + id);
        int confidence = Math.clamp(group.path("score").asInt(0), 0, 100);
        return new ProviderRelease(new Release(new ReleaseId("musicbrainz:" + id), title, artists, year, type, artwork, List.of(source)), confidence);
    }

    private static String text(JsonNode value) {
        if (!value.isString()) throw new IllegalArgumentException("Invalid text");
        var text = value.asString().replaceAll("\\p{Cc}", "");
        if (text.isBlank()) throw new IllegalArgumentException("Missing text");
        return text;
    }

    private static Integer year(String date) {
        try {
            int year = switch (date.length()) {
                case 4 -> Year.parse(date).getValue();
                case 7 -> YearMonth.parse(date).getYear();
                case 10 -> LocalDate.parse(date).getYear();
                default -> 0;
            };
            return year > 0 && year <= 9999 ? year : null;
        } catch (DateTimeException ex) { return null; }
    }

    private static String lucene(String query) {
        // Literal token matching across title/artist fields, without accepting Lucene operators.
        return Arrays.stream(query.split("(?U)\\s+")).filter(token -> !token.isBlank())
                .map(token -> token.replace("\\", "\\\\").replace("\"", "\\\""))
                .map(token -> "(releasegroup:\"" + token + "\" OR artist:\"" + token + "\")")
                .collect(java.util.stream.Collectors.joining(" AND "));
    }
}
