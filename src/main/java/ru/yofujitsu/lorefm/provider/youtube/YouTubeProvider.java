package ru.yofujitsu.lorefm.provider.youtube;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import ru.yofujitsu.lorefm.provider.CatalogHttp;
import ru.yofujitsu.lorefm.release.application.*;
import ru.yofujitsu.lorefm.release.domain.*;
import tools.jackson.databind.JsonNode;
import java.util.*;

@Component
public class YouTubeProvider implements ReleaseProvider {
    private final YouTubeProperties properties;
    private final CatalogHttp http;
    public YouTubeProvider(YouTubeProperties properties) { this.properties = properties; http = new CatalogHttp(properties.getRequestTimeout()); }
    @Override public Provider provider() { return Provider.YOUTUBE; }
    @Override public boolean enabled() { return properties.available(); }
    @Override public List<ProviderRelease> search(ReleaseSearchQuery query) {
        if (!enabled()) return List.of();
        long deadline = System.nanoTime() + properties.getRequestTimeout().toNanos();
        var uri = UriComponentsBuilder.fromUriString(properties.getApiBaseUrl() + "/search")
            .queryParam("part", "snippet").queryParam("type", "video").queryParam("q", "{query}")
            .queryParam("maxResults", query.limit()).encode().buildAndExpand(query.query()).toUri();
        var response = http.get(uri, headers -> headers.set("X-Goog-Api-Key", properties.getApiKey()), deadline);
        if (!response.path("items").isArray()) throw new ProviderUnavailableException(null);
        var found = new ArrayList<ProviderRelease>();
        for (var item : response.path("items")) {
            try { found.add(new ProviderRelease(map(item))); }
            catch (IllegalArgumentException | NullPointerException ignored) { /* Preserve valid neighboring videos. */ }
        }
        CatalogHttp.remaining(deadline);
        return List.copyOf(found);
    }
    private Release map(JsonNode item) {
        String id = CatalogHttp.text(item.path("id").path("videoId"));
        if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Invalid video ID");
        var snippet = item.path("snippet");
        var title = HtmlUtils.htmlUnescape(CatalogHttp.text(snippet.path("title"))).replaceAll("\\p{Cc}", "");
        var channel = HtmlUtils.htmlUnescape(CatalogHttp.text(snippet.path("channelTitle"))).replaceAll("\\p{Cc}", "");
        Artwork artwork = null;
        for (var size : List.of("maxres", "standard", "high", "medium", "default")) {
            var image = snippet.path("thumbnails").path(size);
            var url = CatalogHttp.https(image.path("url").asString(null));
            if (url != null) { artwork = new Artwork(url, dimension(image.path("width")), dimension(image.path("height")), provider()); break; }
        }
        // Upload date is not a release date; preserve the channel attribution without guessing an artist.
        return new Release(new ReleaseId("youtube:" + id), title, List.of(new Artist(channel)), null, ReleaseType.VIDEO_RELEASE, artwork,
            List.of(new ReleaseSource(provider(), id, "https://www.youtube.com/watch?v=" + id)));
    }
    private Integer dimension(JsonNode value) { return value.isIntegralNumber() && value.asLong(-1) >= 0 && value.asLong() <= Integer.MAX_VALUE ? value.asInt() : null; }
}
