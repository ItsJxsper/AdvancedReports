package de.itsjxsper.advancedreports.backend.reports.model;

import de.itsjxsper.advancedreports.backend.reports.eums.Status;

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
        Status status,
        UUID handledByUUID,
        String handlerNote,
        Long screenshotId,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}

