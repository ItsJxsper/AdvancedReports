package de.itsjxsper.advancedreports.common.model.report;

import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.UUID;

public record ReportUpdateDto(
        @NotNull UUID reporterUUID,
        UUID reportedUUID,
        Long categoryId,
        String reason,
        UUID serverUUID,
        String location,
        ReportStatus reportStatus,
        UUID handledByUUID,
        String handlerNote,
        Long screenshotId
) implements Serializable {
}

