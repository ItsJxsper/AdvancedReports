package de.itsjxsper.advancedreports.backend.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportUpdatedEvent {

    private String event = "report.updated";
    private Long reportId;
    private String newStatus;
    private UUID handledBy;
    private Instant timestamp;

    public ReportUpdatedEvent(Long reportId, String newStatus, UUID handledBy, Instant timestamp) {
        this.reportId = reportId;
        this.newStatus = newStatus;
        this.handledBy = handledBy;
        this.timestamp = timestamp;
    }
}

