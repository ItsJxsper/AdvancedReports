package de.itsjxsper.advancedreports.common.model.report;

import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.UUID;

/**
 * Data Transfer Object for updating a report.
 *
 * @param reporterUUID  the UUID of the player who created the report
 * @param reportedUUID  the UUID of the player who was reported
 * @param categoryId    the identifier of the report category
 * @param reason        the reason for the report
 * @param serverUUID    the UUID of the server where the report was created
 * @param location      the location where the incident occurred
 * @param reportStatus  the new status of the report
 * @param handledByUUID the UUID of the player who handled the report
 * @param handlerNote   a note from the person who handled the report
 * @param screenshotId  the identifier of the associated screenshot
 */
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

