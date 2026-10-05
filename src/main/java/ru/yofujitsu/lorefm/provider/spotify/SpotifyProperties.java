package ru.yofujitsu.lorefm.provider.spotify;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties("providers.spotify")
public class SpotifyProperties {
    private boolean enabled = true;
    private String clientId = "";
    private String clientSecret = "";
    private String market = "US";
    private Duration requestTimeout = Duration.ofMillis(1000);
    private String apiBaseUrl = "https://api.spotify.com/v1";
    private String tokenUrl = "https://accounts.spotify.com/api/token";

    public boolean available() {
        return enabled && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
