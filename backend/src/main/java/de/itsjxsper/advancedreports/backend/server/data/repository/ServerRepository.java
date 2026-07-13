package de.itsjxsper.advancedreports.backend.server.data.repository;

import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ServerRepository extends JpaRepository<ServerEntity, UUID> {

    @Query("""
            select count(r)
            from ServerEntity s
            left join s.reportsEntitiesEntities r
            where s.serverUuid = :serverUuid
            """)
    long countReportsByServerUuid(@Param("serverUuid") UUID serverUuid);

    Page<ServerEntity> findAllByOrderByServerUuidAsc(Pageable pageable);
}