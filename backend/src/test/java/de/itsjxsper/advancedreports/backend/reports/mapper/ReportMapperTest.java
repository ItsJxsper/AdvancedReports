package de.itsjxsper.advancedreports.backend.reports.mapper;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportCreateDto;
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
        // Frueher hat die Entity ihr createdAt selbst im Feldinitialisierer gesetzt; das uebernimmt
        // jetzt @CreationTimestamp beim Persistieren, also muss die Fixture es selbst setzen.
        entity.setCreatedAt(java.time.Instant.now());

        var screenshot = TestDataFactory.screenshot("screenshots/2026-01-01/abc-screenshot.png");
        screenshot.setId(9L);
        entity.setScreenshotEntity(screenshot);
    }

    @Nested
    @DisplayName("toDto")
    class ToDto {

        @Test
        @DisplayName("flacht die Assoziationen auf UUIDs und Ids ab")
        void shouldFlattenAssociations() {
            ReportDto dto = mapper.toDto(entity);

            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.reporterUUID()).isEqualTo(REPORTER_UUID);
            assertThat(dto.reportedUUID()).isEqualTo(REPORTED_UUID);
            assertThat(dto.handledByUUID()).isEqualTo(HANDLER_UUID);
            assertThat(dto.categoryId()).isEqualTo(7L);
            assertThat(dto.serverUUID()).isEqualTo(SERVER_UUID);
            assertThat(dto.screenshotId()).isEqualTo(9L);
            assertThat(dto.reason()).isEqualTo("Verdacht auf Fliegen");
            assertThat(dto.location()).isEqualTo("world:100:64:-200");
            assertThat(dto.reportStatus()).isEqualTo(ReportStatus.PENDING);
            assertThat(dto.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("liefert null für optionale Assoziationen, die nicht gesetzt sind")
        void shouldMapMissingOptionalAssociationsToNull() {
            entity.setServer(null);
            entity.setScreenshotEntity(null);

            ReportDto dto = mapper.toDto(entity);

            assertThat(dto.serverUUID()).isNull();
            assertThat(dto.screenshotId()).isNull();
        }

        @Test
        @DisplayName("liefert null für eine null-Entity")
        void shouldMapNullEntityToNull() {
            assertThat(mapper.toDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("bildet die skalaren Felder ab und laesst die Assoziationen dem Service")
        void shouldMapScalarsOnly() {
            ReportCreateDto dto = TestDataFactory.reportCreateDto(
                    REPORTER_UUID, REPORTED_UUID, 7L, SERVER_UUID, HANDLER_UUID);

            ReportsEntity result = mapper.toEntity(dto);

            assertThat(result.getReason()).isEqualTo("Verdacht auf Fliegen");
            assertThat(result.getLocation()).isEqualTo("world:100:64:-200");
            assertThat(result.getReportStatus()).isEqualTo(ReportStatus.PENDING);

            // Attrappen mit gesetzter Id waren transient und liessen save() scheitern; die
            // Assoziationen werden ausschliesslich in ReportService aus der Datenbank geladen.
            assertThat(result.getReporter()).isNull();
            assertThat(result.getReported()).isNull();
            assertThat(result.getHandledBy()).isNull();
            assertThat(result.getCategoryEntity()).isNull();
            assertThat(result.getServer()).isNull();
            assertThat(result.getScreenshotEntity()).isNull();
        }

        @Test
        @DisplayName("laesst eine optionale Assoziation aus, wenn ihre Id null ist")
        void shouldSkipOptionalAssociations() {
            ReportCreateDto dto = new ReportCreateDto(REPORTER_UUID, REPORTED_UUID, 7L, "Grund",
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
        @DisplayName("überschreibt nur die im DTO gesetzten Felder")
        void shouldIgnoreNullValues() {
            ReportUpdateDto dto = new ReportUpdateDto(null, null, null, null, null, null,
                    ReportStatus.APPROVED, null, "Bestätigt und gebannt", null);

            ReportsEntity result = mapper.partialUpdate(dto, entity);

            assertThat(result.getReportStatus()).isEqualTo(ReportStatus.APPROVED);
            assertThat(result.getHandlerNote()).isEqualTo("Bestätigt und gebannt");
            // Unchanged, because the DTO carried null for these.
            assertThat(result.getReason()).isEqualTo("Verdacht auf Fliegen");
            assertThat(result.getLocation()).isEqualTo("world:100:64:-200");
            assertThat(result.getReporter().getPlayerUuid()).isEqualTo(REPORTER_UUID);
            assertThat(result.getCategoryEntity().getId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("laesst die geladenen Assoziationen unangetastet")
        void shouldNotTouchManagedAssociations() {
            ReportUpdateDto dto = new ReportUpdateDto(REPORTER_UUID, null, 42L, null, null, null,
                    null, null, null, null);

            ReportsEntity result = mapper.partialUpdate(dto, entity);

            // Vorher schrieb der Mapper in die *verwaltete* PlayerEntity und haette damit deren
            // Primaerschluessel geaendert; die Kategorie ersetzte er durch eine Attrappe.
            assertThat(result.getReporter().getPlayerUuid()).isEqualTo(REPORTER_UUID);
            assertThat(result.getCategoryEntity().getId()).isEqualTo(7L);
        }

    }
}
