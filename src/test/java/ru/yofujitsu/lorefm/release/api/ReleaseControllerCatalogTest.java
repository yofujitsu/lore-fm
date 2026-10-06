package ru.yofujitsu.lorefm.release.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.yofujitsu.lorefm.provider.musicbrainz.*;
import ru.yofujitsu.lorefm.provider.spotify.*;
import ru.yofujitsu.lorefm.release.application.ReleaseSearchService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReleaseControllerCatalogTest {
    @Test void actualCatalogAdaptersMergeIntoOneApiReleaseWithBothSources() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", e -> {
                String body = switch (e.getRequestURI().getPath()) {
                    case "/api/token" -> "{\"access_token\":\"test-token\",\"expires_in\":3600}";
                    case "/v1/search" -> """
                        {"albums":{"items":[{"id":"123","name":"Cold Visions","album_type":"album",
                        "release_date":"2024-04-24","artists":[{"name":"Bladee"}]}]}}
                        """;
                    case "/ws/2/release-group" -> """
                        {"release-groups":[{"id":"48140466-cff6-3222-bd55-63c27e43190d","title":"Cold Visions",
                        "score":100,"primary-type":"Album","first-release-date":"2024-04-24","artist-credit":[{"name":"Bladee"}]}]}
                        """;
                    default -> "{}";
                };
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type", "application/json");
                e.sendResponseHeaders(200, bytes.length);
                try (var out = e.getResponseBody()) { out.write(bytes); }
            });
            server.start();
            try {
                String base = "http://127.0.0.1:" + server.getAddress().getPort();
                var spotify = new SpotifyProperties();
                spotify.setClientId("test-id"); spotify.setClientSecret("test-secret");
                spotify.setApiBaseUrl(base + "/v1"); spotify.setTokenUrl(base + "/api/token");
                var musicbrainz = new MusicBrainzProperties(); musicbrainz.setApiBaseUrl(base + "/ws/2");
                try (var service = new ReleaseSearchService(List.of(new MusicBrainzProvider(musicbrainz),
                        new SpotifyProvider(spotify, new SpotifyTokenClient(spotify))), Duration.ofMinutes(15),
                        1000, Duration.ofSeconds(2), Duration.ofSeconds(30))) {
                    var mvc = MockMvcBuilders.standaloneSetup(new ReleaseController(service)).build();
                    mvc.perform(get("/api/v1/releases/search").param("q", "Cold Visions"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1))
                            .andExpect(jsonPath("$.results[0].id").value("spotify:123"))
                            .andExpect(jsonPath("$.results[0].artwork.provider").value("MUSICBRAINZ"))
                            .andExpect(jsonPath("$.results[0].sources[0].provider").value("SPOTIFY"))
                            .andExpect(jsonPath("$.results[0].sources[1].provider").value("MUSICBRAINZ"))
                            .andExpect(jsonPath("$.partial").value(false)).andExpect(jsonPath("$.unavailable").value(false));
                    mvc.perform(get("/api/v1/releases/search").param("q", "Cold Visions").param("providers", "MUSICBRAINZ"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].id").value("musicbrainz:48140466-cff6-3222-bd55-63c27e43190d"))
                            .andExpect(jsonPath("$.unavailable").value(false));
                }
            } finally { server.stop(0); }
        }
    }
}
