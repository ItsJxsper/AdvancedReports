package de.itsjxsper.advancedreports.backend.screenshot.data.entity;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

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

    // "s_3_url" war ein von Hand festgeschriebener Artefaktname der Namensstrategie.
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

    // Kein orphanRemoval: ein geloeschter Screenshot ist ein entfernter Anhang, kein Grund den
    // Report zu vernichten. reports_entity.screenshot ist nullable und wird abgehaengt.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "screenshotEntity")
    private Set<ReportsEntity> reportsEntities = new LinkedHashSet<>();

}