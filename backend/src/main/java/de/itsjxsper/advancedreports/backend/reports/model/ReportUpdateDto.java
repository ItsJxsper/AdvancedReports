package de.itsjxsper.advancedreports.backend.reports.model;

import de.itsjxsper.advancedreports.backend.reports.eums.Status;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

public record ReportUpdateDto(
        Optional<UUID> reporterUUID,
        Optional<UUID> reportedUUID,
        Optional<Long> categoryId,
        Optional<String> reason,
        Optional<UUID> serverUUID,
        Optional<String> location,
        Optional<Status> status,
        Optional<UUID> handledByUUID,
        Optional<String> handlerNote,
        Optional<Long> screenshotId
) implements Serializable {
}

