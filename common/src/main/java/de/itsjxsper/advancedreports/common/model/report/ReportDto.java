package de.itsjxsper.advancedreports.common.model.report;

import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ReportDto(
        Long id,
        UUID reporterUUID,
        UUID reportedUUID,
        Long categoryId,
        String reason,
        UUID serverUUID,
        String location,
        ReportStatus reportStatus,
        UUID handledByUUID,
        String handlerNote,
        Long screenshotId,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}

