package de.itsjxsper.advancedreports.backend.category.data.repository;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CategoryRepository")
class CategoryRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PlayerEntity reporter;
    private PlayerEntity reported;
    private PlayerEntity handler;
    private ServerEntity server;

    @BeforeEach
    void persistReferenceData() {
        reporter = entityManager.persist(TestDataFactory.player("Reporter"));
        reported = entityManager.persist(TestDataFactory.player("Reported"));
        handler = entityManager.persist(TestDataFactory.player("Handler"));
        server = entityManager.persist(TestDataFactory.server());
    }

    private CategoryEntity persistCategoryWithReports(String name, int reportCount) {
        CategoryEntity category = entityManager.persist(TestDataFactory.category(name));

        for (int i = 0; i < reportCount; i++) {
            entityManager.persist(TestDataFactory.report(reporter, reported, handler, category, server));
        }

        entityManager.flush();
        return category;
    }

    @Nested
    @DisplayName("findByName and existsByName")
    class FindByName {

        @Test
        @DisplayName("finds a category by its name")
        void shouldFindByName() {
            entityManager.persistAndFlush(TestDataFactory.category("bugs"));

            assertThat(categoryRepository.findByName("bugs"))
                    .isPresent()
                    .get()
                    .satisfies(category -> assertThat(category.getDisplayName()).isEqualTo("Bugs"));
        }

        @Test
        @DisplayName("returns an empty Optional for an unknown name")
        void shouldReturnEmptyForUnknownName() {
            assertThat(categoryRepository.findByName("does-not-exist")).isEmpty();
        }

        @Test
        @DisplayName("is case sensitive")
        void shouldBeCaseSensitive() {
            entityManager.persistAndFlush(TestDataFactory.category("bugs"));

            assertThat(categoryRepository.findByName("BUGS")).isEmpty();
        }

        @Test
        @DisplayName("existsByName reports true for an existing name")
        void shouldReportExistence() {
            entityManager.persistAndFlush(TestDataFactory.category("bugs"));

            assertThat(categoryRepository.existsByName("bugs")).isTrue();
            assertThat(categoryRepository.existsByName("cheating")).isFalse();
        }

        @Test
        @DisplayName("enforces the unique constraint on the name")
        void shouldEnforceUniqueName() {
            entityManager.persistAndFlush(TestDataFactory.category("bugs"));

            assertThatThrownBy(() -> categoryRepository.saveAndFlush(TestDataFactory.category("bugs")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("findWithReportsById")
    class FindWithReportsById {

        @Test
        @DisplayName("loads the reports along through the entity graph")
        void shouldFetchReportsEagerly() {
            CategoryEntity category = persistCategoryWithReports("bugs", 3);
            entityManager.clear();

            var found = categoryRepository.findWithReportsById(category.getId());

            assertThat(found).isPresent();
            // The @EntityGraph has to load the reports along - without it the collection would be lazy
            // and no longer accessible outside the transaction.
            assertThat(found.get().getReportsEntities()).hasSize(3);
        }

        @Test
        @DisplayName("returns an empty report set for a category without reports")
        void shouldReturnEmptyReportsForCategoryWithoutReports() {
            CategoryEntity category = persistCategoryWithReports("bugs", 0);
            entityManager.clear();

            assertThat(categoryRepository.findWithReportsById(category.getId()))
                    .isPresent()
                    .get()
                    .satisfies(c -> assertThat(c.getReportsEntities()).isEmpty());
        }

        @Test
        @DisplayName("returns an empty Optional for an unknown id")
        void shouldReturnEmptyForUnknownId() {
            assertThat(categoryRepository.findWithReportsById(9_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("countReportsPerCategory")
    class CountReportsPerCategory {

        @Test
        @DisplayName("returns one row per category with id, name and report count")
        void shouldCountReportsPerCategory() {
            CategoryEntity withReports = persistCategoryWithReports("bugs", 2);
            CategoryEntity withoutReports = persistCategoryWithReports("cheating", 0);
            entityManager.clear();

            List<Object[]> rows = categoryRepository.countReportsPerCategory();

            assertThat(rows).hasSize(2);
            assertThat(rows)
                    .as("The projection has to return exactly (Long id, String name, Long count) - "
                            + "CategoryService relies on that when casting")
                    .allSatisfy(row -> {
                        assertThat(row).hasSize(3);
                        assertThat(row[0]).isInstanceOf(Long.class);
                        assertThat(row[1]).isInstanceOf(String.class);
                        assertThat(row[2]).isInstanceOf(Long.class);
                    });

            assertThat(rows)
                    .extracting(row -> row[0], row -> row[1], row -> row[2])
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(withReports.getId(), "bugs", 2L),
                            org.assertj.core.groups.Tuple.tuple(withoutReports.getId(), "cheating", 0L));
        }

        @Test
        @DisplayName("returns an empty list when no category exists")
        void shouldReturnEmptyListWithoutCategories() {
            assertThat(categoryRepository.countReportsPerCategory()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findCategoriesWithActiveReports")
    class FindCategoriesWithActiveReports {

        @Test
        @DisplayName("returns only categories that have at least one report")
        void shouldOnlyReturnCategoriesWithReports() {
            persistCategoryWithReports("bugs", 1);
            persistCategoryWithReports("cheating", 0);
            entityManager.clear();

            assertThat(categoryRepository.findCategoriesWithActiveReports())
                    .extracting(CategoryEntity::getName)
                    .containsExactly("bugs");
        }

        @Test
        @DisplayName("returns each category only once, even with several reports")
        void shouldReturnDistinctCategories() {
            persistCategoryWithReports("bugs", 3);
            entityManager.clear();

            assertThat(categoryRepository.findCategoriesWithActiveReports()).hasSize(1);
        }

        @Test
        @DisplayName("returns an empty list when no reports exist")
        void shouldReturnEmptyListWithoutReports() {
            persistCategoryWithReports("bugs", 0);
            entityManager.clear();

            assertThat(categoryRepository.findCategoriesWithActiveReports()).isEmpty();
        }
    }
}
