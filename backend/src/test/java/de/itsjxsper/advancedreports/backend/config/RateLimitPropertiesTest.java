package de.itsjxsper.advancedreports.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitProperties")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("defaults to 100/5/5 requests per second")
    void shouldUseDocumentedDefaults() {
        RateLimitProperties properties = new RateLimitProperties();

        // These values are documented in the README as the default limits.
        assertThat(properties.getServerRequestsPerSecond()).isEqualTo(100);
        assertThat(properties.getPlayerRequestsPerSecond()).isEqualTo(5);
        assertThat(properties.getDiscordRequestsPerSecond()).isEqualTo(5);
    }

    @Test
    @DisplayName("can be overridden through setters")
    void shouldBeOverridable() {
        RateLimitProperties properties = new RateLimitProperties();

        properties.setServerRequestsPerSecond(1);
        properties.setPlayerRequestsPerSecond(2);
        properties.setDiscordRequestsPerSecond(3);

        assertThat(properties.getServerRequestsPerSecond()).isEqualTo(1);
        assertThat(properties.getPlayerRequestsPerSecond()).isEqualTo(2);
        assertThat(properties.getDiscordRequestsPerSecond()).isEqualTo(3);
    }
}
