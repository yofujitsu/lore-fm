package ru.yofujitsu.lorefm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.yofujitsu.lorefm.provider.soundcloud.SoundCloudProperties;
import ru.yofujitsu.lorefm.provider.youtube.YouTubeProperties;

@SpringBootApplication
@EnableConfigurationProperties({SoundCloudProperties.class, YouTubeProperties.class})
public class LoreFmApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoreFmApplication.class, args);
    }

}
