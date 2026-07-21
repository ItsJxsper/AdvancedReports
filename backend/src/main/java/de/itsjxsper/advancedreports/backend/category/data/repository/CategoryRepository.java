package de.itsjxsper.advancedreports.backend.category.data.repository;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    Optional<CategoryEntity> findByName(String name);

    boolean existsByName(String name);

    @EntityGraph(attributePaths = "reportsEntities")
    Optional<CategoryEntity> findWithReportsById(Long id);

    @Query("""
            select c.id, c.name, count(r.id)
            from CategoryEntity c
            left join c.reportsEntities r
            group by c.id, c.name
            """)
    List<Object[]> countReportsPerCategory();

    @Query("""
            select distinct c
            from CategoryEntity c
            join c.reportsEntities r
            where r.categoryEntity.active = true
            """)
    List<CategoryEntity> findCategoriesWithActiveReports();
}
