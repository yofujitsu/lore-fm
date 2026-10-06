package ru.yofujitsu.lorefm.provider.musicbrainz;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Getter @Setter
@ConfigurationProperties("providers.musicbrainz")
public class MusicBrainzProperties {
    private boolean enabled = true;
    private Duration requestTimeout = Duration.ofMillis(1200);
    private String apiBaseUrl = "https://musicbrainz.org/ws/2";
    private String userAgent = "Ninefold/0.1 (mailto:yofujitsuuu@gmail.com)";
}
