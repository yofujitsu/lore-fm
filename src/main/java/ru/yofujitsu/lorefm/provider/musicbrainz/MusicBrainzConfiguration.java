package ru.yofujitsu.lorefm.provider.musicbrainz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MusicBrainzProperties.class)
public class MusicBrainzConfiguration {
    @Bean MusicBrainzProvider musicBrainzProvider(MusicBrainzProperties properties) { return new MusicBrainzProvider(properties); }
}
