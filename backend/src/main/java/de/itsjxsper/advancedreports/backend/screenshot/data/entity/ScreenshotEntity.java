package de.itsjxsper.advancedreports.backend.screenshot.data.entity;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "screenshot_entity")
public class ScreenshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // "s_3_url" was a hand-pinned artefact of the naming strategy.
    @Column(name = "s3_url")
    private String s3Url;

    @Column(name = "s3_object_key", unique = true)
    private String s3ObjectKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "upload_status")
    @Enumerated(EnumType.STRING)
    private UploadStatus uploadStatus;

    // No orphanRemoval: a deleted screenshot is a removed attachment, not a reason to destroy the
    // report. reports_entity.screenshot is nullable and gets detached.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "screenshotEntity")
    private Set<ReportsEntity> reportsEntities = new LinkedHashSet<>();

}