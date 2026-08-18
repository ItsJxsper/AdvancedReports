package de.itsjxsper.advancedreports.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitProperties")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("verwendet 100/5/5 Requests pro Sekunde als Voreinstellung")
    void shouldUseDocumentedDefaults() {
        RateLimitProperties properties = new RateLimitProperties();

        // Diese Werte sind im README als Standardlimits dokumentiert.
        assertThat(properties.getServerRequestsPerSecond()).isEqualTo(100);
        assertThat(properties.getPlayerRequestsPerSecond()).isEqualTo(5);
        assertThat(properties.getDiscordRequestsPerSecond()).isEqualTo(5);
    }

    @Test
    @DisplayName("lässt sich über Setter überschreiben")
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
