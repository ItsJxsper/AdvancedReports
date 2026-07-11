package de.itsjxsper.advancedreports.backend.messaging.events;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenshotReadyEvent {
    private String event = "screenshot.ready";
    private Long reportId;
    private Long screenshotId;
    private String s3Url;
    private Instant timestamp;
}


