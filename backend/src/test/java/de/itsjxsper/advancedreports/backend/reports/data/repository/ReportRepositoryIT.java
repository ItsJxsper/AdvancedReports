package de.itsjxsper.advancedreports.backend.reports.data.repository;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.backend.support.AbstractRepositoryIT;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReportRepository")
class ReportRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PlayerEntity reporter;
    private PlayerEntity reported;
    private PlayerEntity handler;
    private CategoryEntity category;
    private ServerEntity server;

    @BeforeEach
    void persistReferenceData() {
        reporter = entityManager.persist(TestDataFactory.player("Reporter"));
        reported = entityManager.persist(TestDataFactory.player("Reported"));
        handler = entityManager.persist(TestDataFactory.player("Handler"));
        category = entityManager.persist(TestDataFactory.category("cheating"));
        server = entityManager.persist(TestDataFactory.server());
        entityManager.flush();
    }

    private ReportsEntity persistReport() {
        return entityManager.persistAndFlush(
                TestDataFactory.report(reporter, reported, handler, category, server));
    }

    @Nested
    @DisplayName("Persisting")
    class Persisting {

        @Test
        @DisplayName("persists a complete report with every association")
        void shouldPersistReport() {
            ReportsEntity report = persistReport();
            entityManager.clear();

            assertThat(reportRepository.findById(report.getId()))
                    .isPresent()
                    .get()
                    .satisfies(found -> {
                        assertThat(found.getReporter().getPlayerUuid()).isEqualTo(reporter.getPlayerUuid());
                        assertThat(found.getReported().getPlayerUuid()).isEqualTo(reported.getPlayerUuid());
                        assertThat(found.getHandledBy().getPlayerUuid()).isEqualTo(handler.getPlayerUuid());
                        assertThat(found.getCategoryEntity().getId()).isEqualTo(category.getId());
                        assertThat(found.getServer().getServerUuid()).isEqualTo(server.getServerUuid());
                        assertThat(found.getLocation()).isEqualTo("world:100:64:-200");
                    });
        }

        @Test
        @DisplayName("rejects a report without a location because the column is not nullable")
        void shouldRejectReportWithoutLocation() {
            ReportsEntity report = TestDataFactory.report(reporter, reported, handler, category, server);
            report.setLocation(null);

            assertThatThrownBy(() -> reportRepository.saveAndFlush(report))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("rejects a report without a status because the column is not nullable")
        void shouldRejectReportWithoutStatus() {
            ReportsEntity report = TestDataFactory.report(reporter, reported, handler, category, server);
            report.setReportStatus(null);

            assertThatThrownBy(() -> reportRepository.saveAndFlush(report))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("allows a report without a server and without a screenshot")
        void shouldAllowReportWithoutOptionalAssociations() {
            ReportsEntity report = TestDataFactory.report(reporter, reported, handler, category, null);
            report.setScreenshotEntity(null);

            ReportsEntity saved = entityManager.persistAndFlush(report);
            entityManager.clear();

            assertThat(reportRepository.findById(saved.getId()))
                    .isPresent()
                    .get()
                    .satisfies(found -> {
                        assertThat(found.getServer()).isNull();
                        assertThat(found.getScreenshotEntity()).isNull();
                    });
        }
    }

    @Nested
    @DisplayName("findAllByOrderByCreatedAtDesc")
    class FindAllOrdered {

        @Test
        @DisplayName("sorts descending by creation time")
        void shouldSortByCreatedAtDescending() {
            persistReport();
            persistReport();
            persistReport();
            entityManager.clear();

            Page<ReportsEntity> page = reportRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getContent())
                    .extracting(ReportsEntity::getCreatedAt)
                    .isSortedAccordingTo(Comparator.<Instant>naturalOrder().reversed());
        }

        @Test
        @DisplayName("respects the page size and reports the total count")
        void shouldPaginate() {
            persistReport();
            persistReport();
            persistReport();
            entityManager.clear();

            Page<ReportsEntity> firstPage =
                    reportRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 2));

            assertThat(firstPage.getContent()).hasSize(2);
            assertThat(firstPage.getTotalElements()).isEqualTo(3);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
            assertThat(firstPage.hasNext()).isTrue();
        }

        @Test
        @DisplayName("returns an empty page when no reports exist")
        void shouldReturnEmptyPage() {
            assertThat(reportRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Deleting")
    class Deleting {

        @Test
        @DisplayName("deletes a report without removing the referenced players")
        void shouldDeleteReportOnly() {
            ReportsEntity report = persistReport();

            reportRepository.delete(report);
            entityManager.flush();
            entityManager.clear();

            assertThat(reportRepository.findById(report.getId())).isEmpty();
            assertThat(entityManager.find(PlayerEntity.class, reporter.getPlayerUuid())).isNotNull();
            assertThat(entityManager.find(CategoryEntity.class, category.getId())).isNotNull();
        }
    }

    @Nested
    @DisplayName("count")
    class Counting {

        @Test
        @DisplayName("counts every persisted report")
        void shouldCountReports() {
            persistReport();
            persistReport();

            assertThat(reportRepository.count()).isEqualTo(2);
        }
    }
}
