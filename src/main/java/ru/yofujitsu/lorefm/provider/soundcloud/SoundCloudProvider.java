package ru.yofujitsu.lorefm.provider.soundcloud;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import ru.yofujitsu.lorefm.provider.CatalogHttp;
import ru.yofujitsu.lorefm.release.application.*;
import ru.yofujitsu.lorefm.release.domain.*;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class SoundCloudProvider implements ReleaseProvider {
    private record Token(String access, String refresh, long expiresAt) {}
    private final SoundCloudProperties properties;
    private final CatalogHttp http;
    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile Token token;
    public SoundCloudProvider(SoundCloudProperties properties) { this.properties = properties; http = new CatalogHttp(properties.getRequestTimeout()); }
    @Override public Provider provider() { return Provider.SOUNDCLOUD; }
    @Override public boolean enabled() { return properties.available(); }
    @Override public List<ProviderRelease> search(ReleaseSearchQuery query) {
        if (!enabled()) return List.of();
        long deadline = System.nanoTime() + properties.getRequestTimeout().toNanos();
        String access = accessToken(deadline);
        var uri = UriComponentsBuilder.fromUriString(properties.getApiBaseUrl() + "/playlists")
            .queryParam("q", "{query}").queryParam("limit", query.limit()).queryParam("show_tracks", false)
            .queryParam("linked_partitioning", true).encode().buildAndExpand(query.query()).toUri();
        JsonNode response;
        try { response = http.get(uri, headers -> headers.set("Authorization", "OAuth " + access), deadline); }
        catch (CatalogHttp.Failure ex) {
            if (ex.status == 401) invalidate(access);
            throw ex;
        }
        var items = response.isArray() ? response : response.path("collection");
        if (!items.isArray()) throw new ProviderUnavailableException(null);
        var found = new ArrayList<ProviderRelease>();
        for (var item : items) {
            try { found.add(new ProviderRelease(map(item))); }
            catch (IllegalArgumentException | NullPointerException ignored) { /* Preserve valid neighboring sets. */ }
        }
        CatalogHttp.remaining(deadline);
        return List.copyOf(found);
    }
    private String accessToken(long deadline) {
        var current = token;
        if (current != null && System.nanoTime() < current.expiresAt()) return current.access();
        boolean acquired = false;
        try {
            acquired = tokenLock.tryLock(CatalogHttp.remaining(deadline), TimeUnit.NANOSECONDS);
            if (!acquired) throw new ProviderUnavailableException(null);
            current = token;
            if (current != null && System.nanoTime() < current.expiresAt()) return current.access();
            var body = new LinkedMultiValueMap<String, String>();
            boolean refresh = current != null && current.refresh() != null;
            body.add("grant_type", refresh ? "refresh_token" : "client_credentials");
            if (refresh) { body.add("client_id", properties.getClientId()); body.add("client_secret", properties.getClientSecret()); body.add("refresh_token", current.refresh()); }
            JsonNode response;
            try { response = http.post(URI.create(properties.getTokenUrl()), headers -> {
                if (!refresh) headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());
            }, body, deadline); }
            catch (CatalogHttp.Failure ex) { if (ex.status == 400 || ex.status == 401) token = null; throw ex; }
            String access = secret(response.path("access_token"));
            String nextRefresh = response.path("refresh_token").isString() ? secret(response.path("refresh_token")) : null;
            long seconds = response.path("expires_in").asLong(0);
            if (seconds <= 0 || seconds > 86400) throw new ProviderUnavailableException(null);
            token = new Token(access, nextRefresh, System.nanoTime() + Duration.ofSeconds(seconds - Math.min(30, seconds / 10)).toNanos());
            return access;
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ProviderUnavailableException(null); }
        finally { if (acquired) tokenLock.unlock(); }
    }
    private void invalidate(String access) {
        if (!tokenLock.tryLock()) return;
        try { if (token != null && token.access().equals(access)) token = new Token(token.access(), token.refresh(), 0); }
        finally { tokenLock.unlock(); }
    }
    private static String secret(JsonNode value) {
        if (!value.isString() || value.asString().isBlank() || value.asString().length() > 8192 || value.asString().matches("(?s).*\\p{Cc}.*")) throw new ProviderUnavailableException(null);
        return value.asString();
    }
    private Release map(JsonNode item) {
        String urn = item.path("urn").asString("");
        String id = urn.matches("soundcloud:playlists:[0-9]+") ? urn.substring("soundcloud:playlists:".length()) : item.path("id").asString("");
        if (!id.matches("[1-9][0-9]{0,19}")) throw new IllegalArgumentException("Invalid set ID");
        String title = CatalogHttp.text(item.path("title"));
        String creator = CatalogHttp.text(item.path("user").path("username"));
        String kind = item.path("playlist_type").asString("");
        if (kind.isBlank()) kind = item.path("set_type").asString("");
        kind = kind.toLowerCase(Locale.ROOT);
        var type = switch (kind) {
            case "album" -> ReleaseType.ALBUM;
            case "ep" -> ReleaseType.EP;
            case "mixtape" -> ReleaseType.MIXTAPE;
            default -> item.path("is_album").asBoolean(false) ? ReleaseType.ALBUM : ReleaseType.PLAYLIST;
        };
        int year = item.path("release_year").asInt(0);
        String art = CatalogHttp.https(item.path("artwork_url").asString(null));
        String link = CatalogHttp.https(item.path("permalink_url").asString(null));
        if (link != null && !Set.of("soundcloud.com", "www.soundcloud.com").contains(URI.create(link).getHost().toLowerCase(Locale.ROOT))) link = null;
        return new Release(new ReleaseId("soundcloud:" + id), title, List.of(new Artist(creator)), year > 0 && year <= 9999 ? year : null,
            type, art == null ? null : new Artwork(art, null, null, provider()), List.of(new ReleaseSource(provider(), urn.isBlank() ? id : urn, link)));
    }
}
