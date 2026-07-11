package de.itsjxsper.advancedreports.backend.reports.data.repository;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<ReportsEntity, Long> {

    Page<ReportsEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

