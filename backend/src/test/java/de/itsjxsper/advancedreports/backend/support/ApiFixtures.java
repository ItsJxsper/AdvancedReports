package de.itsjxsper.advancedreports.backend.support;

import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creates prerequisite data through the public REST API, so end-to-end tests set up their world the
 * same way a real client would instead of reaching into the database.
 */
public final class ApiFixtures {

    private ApiFixtures() {
    }

    public static PlayerDTO createPlayer(RestClient client, String name) {
        return createPlayer(client, UUID.randomUUID(), name);
    }

    public static PlayerDTO createPlayer(RestClient client, UUID uuid, String name) {
        ResponseEntity<PlayerDTO> response = client.post()
                .uri("/api/v1/player")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TestDataFactory.playerUpdateDto(uuid, name))
                .retrieve()
                .toEntity(PlayerDTO.class);

        assertThat(response.getStatusCode())
                .as("Player %s could not be created", name)
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    public static CategoryDto createCategory(RestClient client, String name) {
        ResponseEntity<CategoryDto> response = client.post()
                .uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TestDataFactory.categoryDto(name))
                .retrieve()
                .toEntity(CategoryDto.class);

        assertThat(response.getStatusCode())
                .as("Category %s could not be created", name)
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /**
     * Creates screenshot metadata without uploading a file.
     * <p>
     * Reports need this: {@code ReportMapperImpl#toEntity} fabricates an empty {@code ScreenshotEntity}
     * whenever {@code screenshotId} is null, and that transient instance breaks the insert. Supplying a
     * real screenshot id is currently the only way to create a report at all — see
     * {@code ReportLifecycleE2ETest}.
     */
    public static ScreenshotDto createScreenshot(RestClient client) {
        String objectKey = "screenshots/fixture/" + UUID.randomUUID() + "-screenshot.png";

        ResponseEntity<ScreenshotDto> response = client.post()
                .uri("/api/v1/screenshots")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TestDataFactory.screenshotUpdateDto(objectKey))
                .retrieve()
                .toEntity(ScreenshotDto.class);

        assertThat(response.getStatusCode())
                .as("Screenshot metadata could not be created")
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
