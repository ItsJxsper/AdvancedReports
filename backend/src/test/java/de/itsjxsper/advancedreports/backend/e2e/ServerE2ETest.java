package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.DbFixtures;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the server endpoints.
 * <p>
 * The read and delete tests still seed their server through SQL: deletion is broken independently
 * of registration (see {@code Deleting} below), so seeding via REST would tie those tests to a
 * defect they are not about.
 */
@DisplayName("E2E: Servers")
class ServerE2ETest extends AbstractE2ETest {

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("registers a server through a JSON body")
        void shouldCreateServerFromJsonBody() {
            UUID serverUuid = UUID.randomUUID();

            ResponseEntity<ServerDto> response = client().post()
                    .uri("/api/v1/servers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TestDataFactory.serverDto(serverUuid))
                    .retrieve()
                    .toEntity(ServerDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().serverUUID()).isEqualTo(serverUuid);
            assertThat(response.getBody().port()).isEqualTo(25565);
        }

    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        @Test
        @DisplayName("returns a server by its UUID")
        void shouldReturnServerByUuid() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            ResponseEntity<ServerDto> response = client().get()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toEntity(ServerDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().serverUUID()).isEqualTo(serverUuid);
            assertThat(response.getBody().port()).isEqualTo(25565);
            assertThat(response.getBody().ipAddress()).isEqualTo(TestDataFactory.loopback());
        }

        @Test
        @DisplayName("answers 404 SERVER_NOT_FOUND for an unknown UUID")
        void shouldReturnNotFound() {
            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/servers/{uuid}", UUID.randomUUID())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SERVER_NOT_FOUND);
        }

        @Test
        @DisplayName("counts the registered servers")
        void shouldCountServers() {
            DbFixtures.insertServer(dataSource);
            DbFixtures.insertServer(dataSource);

            ResponseEntity<Long> response = client().get()
                    .uri("/api/v1/servers/count")
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns a paginated list of servers")
        void shouldListServersPaged() {
            DbFixtures.insertServer(dataSource);
            DbFixtures.insertServer(dataSource);
            DbFixtures.insertServer(dataSource);

            ResponseEntity<String> response = client().get()
                    .uri("/api/v1/servers?page=0&size=2")
                    .retrieve()
                    .toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"totalElements\":3");
        }

        @Test
        @DisplayName("returns 0 reports for a server without reports")
        void shouldReturnZeroReportsForNewServer() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            ResponseEntity<Long> response = client().get()
                    .uri("/api/v1/servers/{uuid}/reports/count", serverUuid)
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isZero();
        }
    }

    @Nested
    @DisplayName("Deleting")
    class Deleting {

        @Test
        @DisplayName("deletes a registered server")
        void shouldDeleteServer() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            assertThat(client().delete()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(client().get()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("answers 404 when deleting an unknown UUID")
        void shouldReturnNotFoundOnDeletingUnknownServer() {
            ResponseEntity<ApiErrorResponse> response = client().delete()
                    .uri("/api/v1/servers/{uuid}", UUID.randomUUID())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SERVER_NOT_FOUND);
        }

    }
}
