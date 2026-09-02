package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.config.RabbitMQConfiguration;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.ApiFixtures;
import de.itsjxsper.advancedreports.backend.support.DbFixtures;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import de.itsjxsper.advancedreports.common.model.report.ReportCreateDto;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The central end-to-end path: create the referenced players, category and server, then push a report
 * through create → read → update → delete over real HTTP, while checking that the corresponding
 * RabbitMQ notifications actually land on the broker.
 */
@DisplayName("E2E: Report-Lebenszyklus")
class ReportLifecycleE2ETest extends AbstractE2ETest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * The only combination that currently makes it past {@code reportRepository.save}: both
     * {@code serverUUID} and {@code screenshotId} have to be present, because only then does
     * {@code ReportService} replace the empty entities that {@code ReportMapperImpl#toEntity} fabricates
     * with managed ones. See {@code shouldCreateReportWithoutScreenshot} for the bug report.
     */
    private PlayerDTO reporter;
    private PlayerDTO reported;
    private PlayerDTO handler;
    private CategoryDto category;
    private UUID serverUuid;
    private Long screenshotId;

    @BeforeEach
    void createReferencedEntities() {
        reporter = ApiFixtures.createPlayer(client(), "Reporter");
        reported = ApiFixtures.createPlayer(client(), "Reported");
        handler = ApiFixtures.createPlayer(client(), "Handler");
        category = ApiFixtures.createCategory(client(), "cheating");
        screenshotId = ApiFixtures.createScreenshot(client()).id();
        // Der Server muss per SQL entstehen, weil POST /api/v1/servers nicht benutzbar ist -
        // siehe ServerE2ETest.
        serverUuid = DbFixtures.insertServer(dataSource);

        drainPluginQueue();
    }

    private ReportCreateDto newReport() {
        return new ReportCreateDto(
                reporter.playerUUID(),
                reported.playerUUID(),
                category.id(),
                "Fliegt seit fünf Minuten über der Spawn-Insel",
                serverUuid,
                "world:120:80:-340",
                ReportStatus.PENDING,
                handler.playerUUID(),
                null,
                screenshotId);
    }

    private ResponseEntity<ReportDto> postReport(ReportCreateDto body) {
        return client().post()
                .uri("/api/v1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(ReportDto.class);
    }

    /**
     * Retries on {@link AmqpException}: the first operation on a fresh channel trips over the
     * undeclared {@code notify.discord} queue, see {@code RabbitMQConfigurationIT}.
     */
    private void drainPluginQueue() {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                while (rabbitTemplate.receive(RabbitMQConfiguration.QUEUE_PLUGIN, 200) != null) {
                    // keep draining
                }
                return;
            } catch (AmqpException ignored) {
                // new channel on the next attempt
            }
        }
    }

    private String receiveEventBody() {
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Message message = rabbitTemplate.receive(RabbitMQConfiguration.QUEUE_PLUGIN, 1_000);
                if (message != null) {
                    return new String(message.getBody(), StandardCharsets.UTF_8);
                }
            } catch (AmqpException ignored) {
                // see drainPluginQueue()
            }
        }
        return null;
    }

    @Nested
    @DisplayName("Anlegen")
    class Creating {

        @Test
        @DisplayName("legt einen Report an und liefert ihn mit id zurück")
        void shouldCreateReport() {
            ResponseEntity<ReportDto> response = postReport(newReport());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ReportDto created = response.getBody();
            assertThat(created).isNotNull();
            assertThat(created.id()).isNotNull();
            assertThat(created.reporterUUID()).isEqualTo(reporter.playerUUID());
            assertThat(created.reportedUUID()).isEqualTo(reported.playerUUID());
            assertThat(created.handledByUUID()).isEqualTo(handler.playerUUID());
            assertThat(created.categoryId()).isEqualTo(category.id());
            assertThat(created.serverUUID()).isEqualTo(serverUuid);
            assertThat(created.reportStatus()).isEqualTo(ReportStatus.PENDING);
            assertThat(created.location()).isEqualTo("world:120:80:-340");
            assertThat(created.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("veröffentlicht ein report.created-Event auf dem Fanout-Exchange")
        void shouldPublishReportCreatedEvent() {
            ResponseEntity<ReportDto> response = postReport(newReport());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            String body = receiveEventBody();

            assertThat(body).isNotNull();
            assertThat(body)
                    .contains("\"event\":\"report.created\"")
                    .contains("\"reportId\":" + response.getBody().id())
                    .contains(serverUuid.toString());
        }

        @Test
        @DisplayName("antwortet mit 404 SERVER_NOT_FOUND für einen unbekannten Server")
        void shouldRejectUnknownServer() {
            ReportCreateDto withUnknownServer = new ReportCreateDto(
                    reporter.playerUUID(), reported.playerUUID(), category.id(), "Grund",
                    UUID.randomUUID(), "world:0:0:0", ReportStatus.PENDING, handler.playerUUID(),
                    null, screenshotId);

            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/reports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(withUnknownServer)
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SERVER_NOT_FOUND);
        }

        @Test
        @DisplayName("legt einen Report ohne Screenshot an")
        void shouldCreateReportWithoutScreenshot() {
            ReportCreateDto withoutScreenshot = new ReportCreateDto(
                    reporter.playerUUID(), reported.playerUUID(), category.id(), "Grund",
                    serverUuid, "world:0:0:0", ReportStatus.PENDING, handler.playerUUID(), null, null);

            ResponseEntity<ReportDto> response = postReport(withoutScreenshot);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().screenshotId()).isNull();
        }

        @Test
        @DisplayName("antwortet mit 404 SCREENSHOT_NOT_FOUND für einen unbekannten Screenshot")
        void shouldRejectUnknownScreenshot() {
            ReportCreateDto withUnknownScreenshot = new ReportCreateDto(
                    reporter.playerUUID(), reported.playerUUID(), category.id(), "Grund",
                    serverUuid, "world:0:0:0", ReportStatus.PENDING, handler.playerUUID(),
                    null, 9_999L);

            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/reports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(withUnknownScreenshot)
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SCREENSHOT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Lesen, Ändern und Löschen")
    class ReadUpdateDelete {

        @Test
        @DisplayName("liest einen angelegten Report über seine id")
        void shouldReadReport() {
            Long reportId = postReport(newReport()).getBody().id();

            ResponseEntity<ReportDto> response = client().get()
                    .uri("/api/v1/reports/{id}", reportId)
                    .retrieve()
                    .toEntity(ReportDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().reason())
                    .isEqualTo("Fliegt seit fünf Minuten über der Spawn-Insel");
        }

        @Test
        @DisplayName("ändert den Status und veröffentlicht ein report.updated-Event")
        void shouldUpdateStatusAndPublishEvent() {
            Long reportId = postReport(newReport()).getBody().id();
            drainPluginQueue();

            // reporterUUID wird hier bewusst mitgeschickt, um zu belegen, dass ein PATCH auch
            // unveraenderte Felder tragen darf. Dass es entfallen kann, prueft
            // shouldUpdateOnlyTheStatus.
            ReportUpdateDto statusChange = new ReportUpdateDto(
                    reporter.playerUUID(), null, null, null, null, null,
                    ReportStatus.APPROVED, null, "Bestätigt und gebannt", null);

            ResponseEntity<ReportDto> response = client().patch()
                    .uri("/api/v1/reports/{id}", reportId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(statusChange)
                    .retrieve()
                    .toEntity(ReportDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().reportStatus()).isEqualTo(ReportStatus.APPROVED);
            assertThat(response.getBody().handlerNote()).isEqualTo("Bestätigt und gebannt");

            String body = receiveEventBody();

            assertThat(body).isNotNull();
            assertThat(body)
                    .contains("\"event\":\"report.updated\"")
                    .contains("\"newStatus\":\"APPROVED\"")
                    .contains(handler.playerUUID().toString());
        }

        @Test
        @DisplayName("schreibt updatedAt fort, sichtbar beim erneuten Lesen des Reports")
        void shouldSetUpdatedAtOnPatch() {
            ReportDto created = postReport(newReport()).getBody();

            // @UpdateTimestamp setzt den Wert schon beim Anlegen, nicht erst bei der ersten
            // Aenderung - anders als das fruehere @PreUpdate, das updatedAt bis dahin null liess.
            assertThat(created.updatedAt()).isNotNull();

            ReportUpdateDto statusChange = new ReportUpdateDto(
                    reporter.playerUUID(), null, null, null, null, null,
                    ReportStatus.APPROVED, null, null, null);

            client().patch()
                    .uri("/api/v1/reports/{id}", created.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(statusChange)
                    .retrieve()
                    .toBodilessEntity();

            ResponseEntity<ReportDto> reread = client().get()
                    .uri("/api/v1/reports/{id}", created.id())
                    .retrieve()
                    .toEntity(ReportDto.class);

            // Der neue Zeitstempel entsteht erst beim Flush am Transaktionsende, also nach dem
            // Mappen der PATCH-Antwort - sichtbar wird er daher beim erneuten Lesen.
            assertThat(reread.getBody().updatedAt()).isAfter(created.updatedAt());
            assertThat(reread.getBody().createdAt()).isEqualTo(created.createdAt());
            assertThat(reread.getBody().reportStatus()).isEqualTo(ReportStatus.APPROVED);
        }

        @Test
        @DisplayName("ändert ausschließlich den Status, ohne weitere Felder mitzuschicken")
        void shouldUpdateOnlyTheStatus() {
            Long reportId = postReport(newReport()).getBody().id();

            ReportUpdateDto onlyStatus = new ReportUpdateDto(
                    null, null, null, null, null, null, ReportStatus.REJECTED, null, null, null);

            ResponseEntity<ReportDto> response = client().patch()
                    .uri("/api/v1/reports/{id}", reportId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(onlyStatus)
                    .retrieve()
                    .toEntity(ReportDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().reportStatus()).isEqualTo(ReportStatus.REJECTED);
        }

        @Test
        @DisplayName("listet Reports paginiert und meldet die Gesamtanzahl")
        void shouldListReports() {
            postReport(newReport());
            postReport(newReport());

            ResponseEntity<String> list = client().get()
                    .uri("/api/v1/reports?page=0&size=1")
                    .retrieve()
                    .toEntity(String.class);

            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(list.getBody()).contains("\"totalElements\":2");

            ResponseEntity<Long> count = client().get()
                    .uri("/api/v1/reports/count")
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(count.getBody()).isEqualTo(2L);
        }

        @Test
        @DisplayName("löscht einen Report und liefert ihn danach nicht mehr aus")
        void shouldDeleteReport() {
            Long reportId = postReport(newReport()).getBody().id();

            assertThat(client().delete()
                    .uri("/api/v1/reports/{id}", reportId)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(client().get()
                    .uri("/api/v1/reports/{id}", reportId)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("antwortet mit 404 REPORT_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() {
            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/reports/9999")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.REPORT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Auswirkungen auf andere Domains")
    class CrossDomainEffects {

        @Test
        @DisplayName("erhöht die Reportanzahl des Servers")
        void shouldCountTowardsServer() {
            postReport(newReport());
            postReport(newReport());

            ResponseEntity<Long> response = client().get()
                    .uri("/api/v1/servers/{uuid}/reports/count", serverUuid)
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(2L);
        }

        @Test
        @DisplayName("erhöht die Reportanzahl der Kategorie")
        void shouldCountTowardsCategory() {
            postReport(newReport());

            ResponseEntity<String> response = client().get()
                    .uri("/api/v1/categories/reports/count")
                    .retrieve()
                    .toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"reportCount\":1");
        }

        @Test
        @DisplayName("lässt die Kategorie in der Liste mit aktiven Reports auftauchen")
        void shouldAppearInCategoriesWithActiveReports() {
            postReport(newReport());

            ResponseEntity<String> response = client().get()
                    .uri("/api/v1/categories/reports/active")
                    .retrieve()
                    .toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("cheating");
        }
    }
}
