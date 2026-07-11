package de.itsjxsper.advancedreports.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .contact(new Contact()
                                .name("ItsJxsper")
                                .email("itsjxsper@outlook.de"))
                        .title("WebBackend API")
                        .description("Swagger API for the AdvancedReports")
                        .version(System.getenv("version")));
    }
}
