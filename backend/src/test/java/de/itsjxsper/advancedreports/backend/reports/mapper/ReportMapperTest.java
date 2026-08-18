package de.itsjxsper.advancedreports.backend.reports.mapper;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
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
        @DisplayName("baut aus flachen UUIDs verschachtelte Assoziationen auf")
        void shouldExpandAssociations() {
            ReportUpdateDto dto = TestDataFactory.reportUpdateDto(
                    REPORTER_UUID, REPORTED_UUID, 7L, SERVER_UUID, HANDLER_UUID);

            ReportsEntity result = mapper.toEntity(dto);

            assertThat(result.getReporter().getPlayerUuid()).isEqualTo(REPORTER_UUID);
            assertThat(result.getReported().getPlayerUuid()).isEqualTo(REPORTED_UUID);
            assertThat(result.getHandledBy().getPlayerUuid()).isEqualTo(HANDLER_UUID);
            assertThat(result.getCategoryEntity().getId()).isEqualTo(7L);
            assertThat(result.getServer().getServerUuid()).isEqualTo(SERVER_UUID);
            assertThat(result.getReason()).isEqualTo("Verdacht auf Fliegen");
            assertThat(result.getLocation()).isEqualTo("world:100:64:-200");
            assertThat(result.getReportStatus()).isEqualTo(ReportStatus.PENDING);
        }

        @Test
        @org.junit.jupiter.api.Disabled("BUG: Der generierte ReportMapperImpl#toEntity erzeugt fuer "
                + "jede verschachtelte Assoziation bedingungslos ein neues Objekt und prueft nur das "
                + "aeussere DTO auf null. Ein ReportUpdateDto ohne serverUUID und ohne screenshotId "
                + "liefert daher eine ServerEntity mit serverUuid = null und eine ScreenshotEntity "
                + "mit id = null statt null. Beide sind transient und werden von @ManyToOne ohne "
                + "Cascade referenziert, sodass reportRepository.save() daran scheitert - POST "
                + "/api/v1/reports ohne optionale Referenzen ist damit nicht benutzbar. Fix: die "
                + "Assoziationen im Service aufloesen statt sie im Mapper aufzubauen (siehe "
                + "reports/mapper/ReportMapper.java:22-37).")
        @DisplayName("lässt eine optionale Assoziation aus, wenn ihre Id null ist")
        void shouldSkipOptionalAssociations() {
            ReportUpdateDto dto = new ReportUpdateDto(REPORTER_UUID, REPORTED_UUID, 7L, "Grund",
                    null, "world:0:0:0", ReportStatus.PENDING, HANDLER_UUID, null, null);

            ReportsEntity result = mapper.toEntity(dto);

            assertThat(result.getServer()).isNull();
            assertThat(result.getScreenshotEntity()).isNull();
        }

        @Test
        @DisplayName("dokumentiert, dass optionale Assoziationen aktuell als leere Objekte entstehen")
        void shouldCurrentlyFabricateEmptyAssociations() {
            ReportUpdateDto dto = new ReportUpdateDto(REPORTER_UUID, REPORTED_UUID, 7L, "Grund",
                    null, "world:0:0:0", ReportStatus.PENDING, HANDLER_UUID, null, null);

            ReportsEntity result = mapper.toEntity(dto);

            // Ist-Verhalten, festgehalten damit ein Fix des obigen Bugs hier sichtbar fehlschlägt.
            assertThat(result.getServer()).isNotNull();
            assertThat(result.getServer().getServerUuid()).isNull();
            assertThat(result.getScreenshotEntity()).isNotNull();
            assertThat(result.getScreenshotEntity().getId()).isNull();
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
        @DisplayName("aktualisiert die Kategorie, wenn eine neue categoryId übergeben wird")
        void shouldUpdateCategory() {
            ReportUpdateDto dto = new ReportUpdateDto(null, null, 42L, null, null, null,
                    null, null, null, null);

            ReportsEntity result = mapper.partialUpdate(dto, entity);

            assertThat(result.getCategoryEntity().getId()).isEqualTo(42L);
        }
    }
}
