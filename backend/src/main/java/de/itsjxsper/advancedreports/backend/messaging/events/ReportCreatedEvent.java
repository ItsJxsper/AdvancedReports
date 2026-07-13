package de.itsjxsper.advancedreports.backend.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportCreatedEvent {

    private String event = "report.created";
    private Long reportId;
    //TODO: Maybe we can remove the serverUuid and timestamp from the event, because we can get this information from the reportId in the ReportService when we receive the event. This would make the event smaller and easier to handle.
    private UUID serverUuid;
    private Instant timestamp;

    public ReportCreatedEvent(Long reportId, UUID serverUuid, Instant timestamp) {
        this.reportId = reportId;
        this.serverUuid = serverUuid;
        this.timestamp = timestamp;
    }
}
