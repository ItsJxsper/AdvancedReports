package de.itsjxsper.advancedreports.backend.reports.exceptions;

public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(Long reportId) {
        super("Report with ID " + reportId + " was not found");
    }
}

