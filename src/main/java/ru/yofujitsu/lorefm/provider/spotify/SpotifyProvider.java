package ru.yofujitsu.lorefm.provider.spotify;

import org.springframework.web.util.UriComponentsBuilder;
import ru.yofujitsu.lorefm.release.application.*;
import ru.yofujitsu.lorefm.release.domain.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SpotifyProvider implements ReleaseProvider {
    private final SpotifyProperties properties;
    private final SpotifyTokenClient tokens;
    private final SpotifyHttp http;
    private final JsonMapper mapper = new JsonMapper();

    public SpotifyProvider(SpotifyProperties properties, SpotifyTokenClient tokens) {
        this.properties = properties;
        this.tokens = tokens;
        this.http = new SpotifyHttp(properties.getRequestTimeout());
    }

    @Override public Provider provider() { return Provider.SPOTIFY; }

    @Override public List<ProviderRelease> search(ReleaseSearchQuery query) {
        if (!properties.available()) return List.of();
        long deadline = System.nanoTime() + properties.getRequestTimeout().toNanos();
        var token = tokens.accessToken(deadline);
        var uri = UriComponentsBuilder.fromUriString(properties.getApiBaseUrl() + "/search")
                .queryParam("q", "{query}").queryParam("type", "album").queryParam("market", properties.getMarket())
                .queryParam("limit", Math.min(query.limit(), 10)).encode().buildAndExpand(query.query()).toUri();
        SpotifyResponse response;
        try {
            response = http.client(deadline).get().uri(uri).headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(SpotifyResponse.class);
        } catch (SpotifyHttp.Failure ex) {
            if (ex.status == 401) tokens.invalidateToken();
            throw ex;
        }
        if (response == null || response.albums() == null || response.albums().items() == null) {
            throw new IllegalStateException("Invalid Spotify search response");
        }
        var results = new ArrayList<ProviderRelease>();
        for (var album : response.albums().items()) {
            if (album == null) continue;
            try { results.add(new ProviderRelease(map(mapper.treeToValue(album, SpotifyResponse.Album.class)))); }
            catch (JacksonException | IllegalArgumentException | NullPointerException ignored) { /* Decode items independently to preserve valid neighbors. */ }
        }
        return List.copyOf(results);
    }

    private Release map(SpotifyResponse.Album album) {
        if (album.id() == null || !album.id().matches("[A-Za-z0-9]+")) throw new IllegalArgumentException("Invalid release id");
        var artists = album.artists().stream().filter(a -> a != null && a.name() != null && !a.name().isBlank())
                .map(a -> new Artist(cleanText(a.name()))).toList();
        if (artists.isEmpty()) throw new IllegalArgumentException("Missing artists");
        var type = switch (album.albumType() == null ? "" : album.albumType()) {
            case "album" -> ReleaseType.ALBUM;
            case "single" -> ReleaseType.SINGLE;
            case "compilation" -> ReleaseType.COMPILATION;
            default -> ReleaseType.UNKNOWN;
        };
        Artwork artwork = null;
        if (album.images() != null) {
            artwork = album.images().stream().filter(i -> i != null && https(i.url()) != null)
                    .max(Comparator.comparingInt(i -> i.width() == null ? 0 : i.width()))
                    .map(i -> new Artwork(i.url(), i.width(), i.height(), provider())).orElse(null);
        }
        Integer year = releaseYear(album.releaseDate());
        String url = album.externalUrls() == null ? null : https(album.externalUrls().spotify());
        if (url == null) url = "https://open.spotify.com/album/" + album.id();
        return new Release(new ReleaseId("spotify:" + album.id()), cleanText(album.name()), artists, year, type, artwork,
                List.of(new ReleaseSource(provider(), album.id(), url)));
    }

    private static String cleanText(String text) {
        if (text == null) throw new IllegalArgumentException("Missing text");
        return text.replaceAll("\\p{Cc}", "");
    }

    private static Integer releaseYear(String date) {
        if (date == null) return null;
        try {
            return switch (date.length()) {
                case 4 -> Year.parse(date).getValue();
                case 7 -> YearMonth.parse(date).getYear();
                case 10 -> LocalDate.parse(date).getYear();
                default -> null;
            };
        } catch (DateTimeException ex) { return null; }
    }

    private static String https(String value) {
        if (value == null) return null;
        try {
            var uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException ex) { return null; }
    }
}
