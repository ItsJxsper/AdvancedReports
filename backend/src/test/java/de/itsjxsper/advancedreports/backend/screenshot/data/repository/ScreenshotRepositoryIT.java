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
    @DisplayName("Speichern")
    class Persisting {

        @Test
        @DisplayName("speichert alle Metadatenfelder")
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
        @DisplayName("speichert den Upload-Status als Text, nicht als Ordinalzahl")
        void shouldPersistUploadStatusAsString() {
            ScreenshotEntity screenshot = TestDataFactory.screenshot(OBJECT_KEY);
            screenshot.setUploadStatus(UploadStatus.FAILED);
            ScreenshotEntity saved = entityManager.persistAndFlush(screenshot);
            entityManager.clear();

            // @Enumerated(EnumType.STRING) - andernfalls wuerde ein spaeter eingefuegter Enum-Wert
            // alle bestehenden Zeilen umdeuten.
            Object status = entityManager.getEntityManager()
                    .createNativeQuery("select upload_status from screenshot_entity where id = :id")
                    .setParameter("id", saved.getId())
                    .getSingleResult();

            assertThat(status).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("erlaubt Metadaten ganz ohne Object-Key")
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
        @DisplayName("erzwingt die Unique-Constraint auf dem S3-Object-Key")
        void shouldEnforceUniqueObjectKey() {
            entityManager.persistAndFlush(TestDataFactory.screenshot(OBJECT_KEY));

            assertThatThrownBy(() ->
                    screenshotRepository.saveAndFlush(TestDataFactory.screenshot(OBJECT_KEY)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("erlaubt mehrere Metadatensätze ohne Object-Key")
        void shouldAllowMultipleNullObjectKeys() {
            entityManager.persist(TestDataFactory.screenshot(null));
            entityManager.persist(TestDataFactory.screenshot(null));

            // In Postgres kollidieren NULL-Werte in einem Unique-Index nicht.
            entityManager.flush();

            assertThat(screenshotRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findAll und count")
    class ListOperations {

        @Test
        @DisplayName("liefert Screenshots paginiert und meldet die Gesamtanzahl")
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
