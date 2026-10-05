package ru.yofujitsu.lorefm.provider.spotify;

import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SpotifyProperties.class)
public class SpotifyConfiguration {
    @Bean
    SpotifyTokenClient spotifyTokenClient(SpotifyProperties properties) { return new SpotifyTokenClient(properties); }

    @Bean
    SpotifyProvider spotifyProvider(SpotifyProperties properties, SpotifyTokenClient tokens) {
        if (!properties.available()) LoggerFactory.getLogger(SpotifyConfiguration.class).info("Spotify search disabled or credentials not configured");
        return new SpotifyProvider(properties, tokens);
    }
}
