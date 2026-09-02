package de.itsjxsper.advancedreports.backend.screenshot.data.repository;

import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.support.AbstractRepositoryIT;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ScreenshotRepository")
class ScreenshotRepositoryIT extends AbstractRepositoryIT {

    private static final String OBJECT_KEY = "screenshots/2026-01-01/abc-screenshot.png";

    @Autowired
    private ScreenshotRepository screenshotRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Nested
    @DisplayName("Persisting")
    class Persisting {

        @Test
        @DisplayName("persists every metadata field")
        void shouldPersistMetadata() {
            ScreenshotEntity saved = entityManager.persistAndFlush(TestDataFactory.screenshot(OBJECT_KEY));
            entityManager.clear();

            assertThat(screenshotRepository.findById(saved.getId()))
                    .isPresent()
                    .get()
                    .satisfies(screenshot -> {
                        assertThat(screenshot.getS3ObjectKey()).isEqualTo(OBJECT_KEY);
                        assertThat(screenshot.getOriginalFilename()).isEqualTo("screenshot.png");
                        assertThat(screenshot.getContentType()).isEqualTo("image/png");
                        assertThat(screenshot.getFileSizeBytes()).isEqualTo(1024L);
                        assertThat(screenshot.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
                    });
        }

        @Test
        @DisplayName("persists the upload status as text, not as an ordinal")
        void shouldPersistUploadStatusAsString() {
            ScreenshotEntity screenshot = TestDataFactory.screenshot(OBJECT_KEY);
            screenshot.setUploadStatus(UploadStatus.FAILED);
            ScreenshotEntity saved = entityManager.persistAndFlush(screenshot);
            entityManager.clear();

            // @Enumerated(EnumType.STRING) - otherwise an enum value inserted later would
            // reinterpret every existing row.
            Object status = entityManager.getEntityManager()
                    .createNativeQuery("select upload_status from screenshot_entity where id = :id")
                    .setParameter("id", saved.getId())
                    .getSingleResult();

            assertThat(status).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("allows metadata with no object key at all")
        void shouldAllowMissingObjectKey() {
            ScreenshotEntity screenshot = TestDataFactory.screenshot(null);

            ScreenshotEntity saved = entityManager.persistAndFlush(screenshot);

            assertThat(saved.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("enforces the unique constraint on the S3 object key")
        void shouldEnforceUniqueObjectKey() {
            entityManager.persistAndFlush(TestDataFactory.screenshot(OBJECT_KEY));

            assertThatThrownBy(() ->
                    screenshotRepository.saveAndFlush(TestDataFactory.screenshot(OBJECT_KEY)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("allows several metadata rows without an object key")
        void shouldAllowMultipleNullObjectKeys() {
            entityManager.persist(TestDataFactory.screenshot(null));
            entityManager.persist(TestDataFactory.screenshot(null));

            // In Postgres, NULL values do not collide in a unique index.
            entityManager.flush();

            assertThat(screenshotRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findAll and count")
    class ListOperations {

        @Test
        @DisplayName("returns screenshots with pagination and reports the total count")
        void shouldPaginate() {
            entityManager.persist(TestDataFactory.screenshot("screenshots/a.png"));
            entityManager.persist(TestDataFactory.screenshot("screenshots/b.png"));
            entityManager.persist(TestDataFactory.screenshot("screenshots/c.png"));
            entityManager.flush();
            entityManager.clear();

            var page = screenshotRepository.findAll(PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
        }
    }
}
