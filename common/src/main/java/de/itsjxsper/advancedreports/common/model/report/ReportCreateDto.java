package de.itsjxsper.advancedreports.common.model.report;

import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.UUID;

/**
 * Payload for filing a new report.
 * <p>
 * Split out from {@link ReportUpdateDto}, which served both POST and PATCH. Sharing one record made
 * the two operations contradict each other: {@code reporterUUID} had to be {@code @NotNull} for a
 * create, which meant every partial update had to resend it, while the fields a create genuinely
 * requires - {@code reportedUUID}, {@code categoryId}, {@code location} - could not be marked at all
 * and only failed later against the NOT NULL columns.
 * <p>
 * {@code reportStatus} is optional: a newly filed report is {@code PENDING} unless stated otherwise.
 * {@code handledByUUID} is optional too - a fresh report has no handler.
 *
 * @param reporterUUID  the player filing the report
 * @param reportedUUID  the player being reported
 * @param categoryId    the category this report falls under
 * @param reason        free-text reason, optional
 * @param serverUUID    the server the report was filed on, optional
 * @param location      where in the world the report was filed
 * @param reportStatus  initial status; defaults to {@link ReportStatus#PENDING} when omitted
 * @param handledByUUID the moderator handling the report, optional
 * @param handlerNote   free-text note from the handler, optional
 * @param screenshotId  an attached screenshot, optional
 */
public record ReportCreateDto(
        @NotNull UUID reporterUUID,
        @NotNull UUID reportedUUID,
        @NotNull Long categoryId,
        String reason,
        UUID serverUUID,
        @NotNull String location,
        ReportStatus reportStatus,
        UUID handledByUUID,
        String handlerNote,
        Long screenshotId
) implements Serializable {
}
