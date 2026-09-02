package de.itsjxsper.advancedreports.backend;

import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BackendApplication")
class BackendApplicationIT extends AbstractE2ETest {

    @Test
    @DisplayName("starts the full application context")
    void contextLoads() {
        // Fails if any bean cannot be created. Notably RedisConfig#proxyManager opens a Lettuce
        // connection eagerly, so this test only passes with a reachable Redis.
    }
}
