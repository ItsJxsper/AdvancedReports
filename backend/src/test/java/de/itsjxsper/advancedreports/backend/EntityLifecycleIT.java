package de.itsjxsper.advancedreports.backend;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.backend.support.AbstractRepositoryIT;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the JPA lifecycle behaviour that lives in the entities themselves — field initialisers and
 * {@code @PreUpdate} callbacks — rather than in any service.
 */
@DisplayName("Entity-Lebenszyklus")
class EntityLifecycleIT extends AbstractRepositoryIT {

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

    @Nested
    @DisplayName("ReportsEntity")
    class Reports {

        @Test
        @DisplayName("setzt createdAt beim Anlegen")
        void shouldSetCreatedAt() {
            Instant before = Instant.now();
            ReportsEntity saved = entityManager.persistAndFlush(
                    TestDataFactory.report(reporter, reported, handler, category, server));
            entityManager.clear();

            ReportsEntity found = entityManager.find(ReportsEntity.class, saved.getId());

            assertThat(found.getCreatedAt())
                    .isNotNull()
                    .isAfterOrEqualTo(before.minusSeconds(1));
        }

        @Test
        @DisplayName("lässt updatedAt beim Anlegen leer")
        void shouldLeaveUpdatedAtNullOnInsert() {
            ReportsEntity saved = entityManager.persistAndFlush(
                    TestDataFactory.report(reporter, reported, handler, category, server));
            entityManager.clear();

            assertThat(entityManager.find(ReportsEntity.class, saved.getId()).getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("füllt updatedAt über @PreUpdate bei einer Änderung")
        void shouldSetUpdatedAtOnUpdate() {
            ReportsEntity saved = entityManager.persistAndFlush(
                    TestDataFactory.report(reporter, reported, handler, category, server));

            saved.setReportStatus(ReportStatus.APPROVED);
            saved.setHandlerNote("Bestätigt");
            entityManager.flush();
            entityManager.clear();

            ReportsEntity found = entityManager.find(ReportsEntity.class, saved.getId());

            assertThat(found.getUpdatedAt()).isNotNull();
            assertThat(found.getReportStatus()).isEqualTo(ReportStatus.APPROVED);
        }

        @Test
        @DisplayName("speichert den Status als Text, nicht als Ordinalzahl")
        void shouldPersistStatusAsString() {
            ReportsEntity saved = entityManager.persistAndFlush(
                    TestDataFactory.report(reporter, reported, handler, category, server));
            entityManager.clear();

            Object status = entityManager.getEntityManager()
                    .createNativeQuery("select status from reports_entity where id = :id")
                    .setParameter("id", saved.getId())
                    .getSingleResult();

            assertThat(status).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("CategoryEntity")
    class Categories {

        @Test
        @DisplayName("ist standardmäßig aktiv")
        void shouldDefaultToActive() {
            CategoryEntity saved = entityManager.persistAndFlush(TestDataFactory.category("bugs"));
            entityManager.clear();

            assertThat(entityManager.find(CategoryEntity.class, saved.getId()).getActive()).isTrue();
        }

        @Test
        @DisplayName("schreibt active auch in der Datenbank auf true")
        void shouldPersistActiveAsTrue() {
            CategoryEntity saved = entityManager.persistAndFlush(TestDataFactory.category("bugs"));
            entityManager.clear();

            Object active = entityManager.getEntityManager()
                    .createNativeQuery("select active from categories_entity where id = :id")
                    .setParameter("id", saved.getId())
                    .getSingleResult();

            // Siehe CategoryMapperTest: active ist final und laesst sich nie auf false setzen.
            assertThat(active).isEqualTo(Boolean.TRUE);
        }
    }
}
