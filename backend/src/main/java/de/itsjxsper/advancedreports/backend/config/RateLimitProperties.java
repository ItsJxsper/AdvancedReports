package de.itsjxsper.advancedreports.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private int serverRequestsPerSecond = 100;
    private int playerRequestsPerSecond = 5;
    private int discordRequestsPerSecond = 5;
}
