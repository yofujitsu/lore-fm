package ru.yofujitsu.lorefm.provider.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record SpotifyResponse(Albums albums) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Albums(List<JsonNode> items) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Album(String id, String name, @JsonProperty("album_type") String albumType,
                 @JsonProperty("release_date") String releaseDate, List<SpotifyArtist> artists,
                 List<Image> images, @JsonProperty("external_urls") ExternalUrls externalUrls) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SpotifyArtist(String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Image(String url, Integer width, Integer height) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExternalUrls(String spotify) {}
}
