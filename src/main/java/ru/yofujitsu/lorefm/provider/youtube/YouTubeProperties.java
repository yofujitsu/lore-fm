package ru.yofujitsu.lorefm.provider.youtube;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Getter @Setter
@ConfigurationProperties("providers.youtube")
public class YouTubeProperties {
    private boolean enabled;
    private String apiKey = "";
    private Duration requestTimeout = Duration.ofMillis(1500);
    private String apiBaseUrl = "https://www.googleapis.com/youtube/v3";
    public boolean available() { return enabled && apiKey != null && !apiKey.isBlank(); }
}
