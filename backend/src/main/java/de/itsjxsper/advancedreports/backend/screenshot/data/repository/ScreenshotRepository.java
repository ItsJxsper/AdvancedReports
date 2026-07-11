package de.itsjxsper.advancedreports.backend.screenshot.data.repository;

import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenshotRepository extends JpaRepository<ScreenshotEntity, Long> {
}