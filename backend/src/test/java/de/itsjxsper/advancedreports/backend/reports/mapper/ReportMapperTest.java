package de.itsjxsper.advancedreports.backend.reports.mapper;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import de.itsjxsper.advancedreports.common.model.report.ReportCreateDto;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the generated MapStruct implementation directly.
 * <p>
 * The mapper is declared with {@code componentModel = SPRING}, so there is no {@code Mappers.getMapper}
 * factory — the generated {@code ReportMapperImpl} is instantiated by hand instead. That keeps this a
 * plain unit test while still testing the real generated code rather than a hand-written stand-in.
 */
@DisplayName("ReportMapper")
class ReportMapperTest {

    private static final UUID REPORTER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPORTED_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HANDLER_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SERVER_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final ReportMapper mapper = new ReportMapperImpl();

    private ReportsEntity entity;

    @BeforeEach
    void setUp() {
        var reporter = TestDataFactory.player(REPORTER_UUID, "Reporter");
        var reported = TestDataFactory.player(REPORTED_UUID, "Reported");
        var handler = TestDataFactory.player(HANDLER_UUID, "Handler");
        var category = TestDataFactory.category("cheating");
        category.setId(7L);
        var server = TestDataFactory.server();
        server.setServerUuid(SERVER_UUID);

        entity = TestDataFactory.report(reporter, reported, handler, category, server);
        entity.setId(1L);
        // The entity used to set its own createdAt in a field initialiser; @CreationTimestamp now
        // does that while persisting, so the fixture has to set it itself.
        entity.setCreatedAt(java.time.Instant.now());

        var screenshot = TestDataFactory.screenshot("screenshots/2026-01-01/abc-screenshot.png");
        screenshot.setId(9L);
        entity.setScreenshotEntity(screenshot);
    }

    @Nested
    @DisplayName("toDto")
    class ToDto {

        @Test
        @DisplayName("flattens the associations to UUIDs and ids")
        void shouldFlattenAssociations() {
            ReportDto dto = mapper.toDto(entity);

            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.reporterUUID()).isEqualTo(REPORTER_UUID);
            assertThat(dto.reportedUUID()).isEqualTo(REPORTED_UUID);
            assertThat(dto.handledByUUID()).isEqualTo(HANDLER_UUID);
            assertThat(dto.categoryId()).isEqualTo(7L);
            assertThat(dto.serverUUID()).isEqualTo(SERVER_UUID);
            assertThat(dto.screenshotId()).isEqualTo(9L);
            assertThat(dto.reason()).isEqualTo("Suspected of flying");
            assertThat(dto.location()).isEqualTo("world:100:64:-200");
            assertThat(dto.reportStatus()).isEqualTo(ReportStatus.PENDING);
            assertThat(dto.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("returns null for optional associations that are not set")
        void shouldMapMissingOptionalAssociationsToNull() {
            entity.setServer(null);
            entity.setScreenshotEntity(null);

            ReportDto dto = mapper.toDto(entity);

            assertThat(dto.serverUUID()).isNull();
            assertThat(dto.screenshotId()).isNull();
        }

        @Test
        @DisplayName("returns null for a null entity")
        void shouldMapNullEntityToNull() {
            assertThat(mapper.toDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("maps the scalar fields and leaves the associations to the service")
        void shouldMapScalarsOnly() {
            ReportCreateDto dto = TestDataFactory.reportCreateDto(
                    REPORTER_UUID, REPORTED_UUID, 7L, SERVER_UUID, HANDLER_UUID);

            ReportsEntity result = mapper.toEntity(dto);

            assertThat(result.getReason()).isEqualTo("Suspected of flying");
            assertThat(result.getLocation()).isEqualTo("world:100:64:-200");
            assertThat(result.getReportStatus()).isEqualTo(ReportStatus.PENDING);

            // Stubs carrying an id were transient and made save() fail; the associations are loaded
            // from the database exclusively in ReportService.
            assertThat(result.getReporter()).isNull();
            assertThat(result.getReported()).isNull();
            assertThat(result.getHandledBy()).isNull();
            assertThat(result.getCategoryEntity()).isNull();
            assertThat(result.getServer()).isNull();
            assertThat(result.getScreenshotEntity()).isNull();
        }

        @Test
        @DisplayName("omits an optional association when its id is null")
        void shouldSkipOptionalAssociations() {
            ReportCreateDto dto = new ReportCreateDto(REPORTER_UUID, REPORTED_UUID, 7L, "Reason",
                    null, "world:0:0:0", ReportStatus.PENDING, HANDLER_UUID, null, null);

            ReportsEntity result = mapper.toEntity(dto);

            assertThat(result.getServer()).isNull();
            assertThat(result.getScreenshotEntity()).isNull();
        }
    }

    @Nested
    @DisplayName("partialUpdate")
    class PartialUpdate {

        @Test
        @DisplayName("overwrites only the fields set in the DTO")
        void shouldIgnoreNullValues() {
            ReportUpdateDto dto = new ReportUpdateDto(null, null, null, null, null, null,
                    ReportStatus.APPROVED, null, "Confirmed and banned", null);

            ReportsEntity result = mapper.partialUpdate(dto, entity);

            assertThat(result.getReportStatus()).isEqualTo(ReportStatus.APPROVED);
            assertThat(result.getHandlerNote()).isEqualTo("Confirmed and banned");
            // Unchanged, because the DTO carried null for these.
            assertThat(result.getReason()).isEqualTo("Suspected of flying");
            assertThat(result.getLocation()).isEqualTo("world:100:64:-200");
            assertThat(result.getReporter().getPlayerUuid()).isEqualTo(REPORTER_UUID);
            assertThat(result.getCategoryEntity().getId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("leaves the loaded associations untouched")
        void shouldNotTouchManagedAssociations() {
            ReportUpdateDto dto = new ReportUpdateDto(REPORTER_UUID, null, 42L, null, null, null,
                    null, null, null, null);

            ReportsEntity result = mapper.partialUpdate(dto, entity);

            // The mapper used to write into the *managed* PlayerEntity and would have changed its
            // primary key; it replaced the category with a stub.
            assertThat(result.getReporter().getPlayerUuid()).isEqualTo(REPORTER_UUID);
            assertThat(result.getCategoryEntity().getId()).isEqualTo(7L);
        }

    }
}
