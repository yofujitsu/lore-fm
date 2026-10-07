package ru.yofujitsu.lorefm.provider.soundcloud;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Getter @Setter
@ConfigurationProperties("providers.soundcloud")
public class SoundCloudProperties {
    private boolean enabled;
    private String clientId = "";
    private String clientSecret = "";
    private Duration requestTimeout = Duration.ofMillis(1500);
    private String apiBaseUrl = "https://api.soundcloud.com";
    private String tokenUrl = "https://secure.soundcloud.com/oauth/token";
    public boolean available() { return enabled && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank(); }
}
