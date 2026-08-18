package de.itsjxsper.advancedreports.backend;

import org.springframework.boot.SpringApplication;

public class TestBackendApplication {

    static void main() {
        SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run();
    }
}
