package de.itsjxsper.advancedreports.backend.reports.data.entity;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Postgres does not index foreign keys on its own, so every count query and every cascade walked the
 * whole table. The list endpoint sorts by {@code created_at}, which had no index either.
 */
@Getter
@Setter
@Entity
@Table(name = "reports_entity", indexes = {
        @Index(name = "idx_reports_created_at", columnList = "created_at"),
        @Index(name = "idx_reports_status", columnList = "status"),
        @Index(name = "idx_reports_reporter", columnList = "reporter_uuid"),
        @Index(name = "idx_reports_reported", columnList = "reported_uuid"),
        @Index(name = "idx_reports_category", columnList = "category_entity_id"),
        @Index(name = "idx_reports_server", columnList = "server"),
        @Index(name = "idx_reports_handled_by", columnList = "handled_by_player_uuid"),
        @Index(name = "idx_reports_screenshot", columnList = "screenshot")
})
public class ReportsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_uuid", nullable = false)
    private PlayerEntity reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_uuid", nullable = false)
    private PlayerEntity reported;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_entity_id", nullable = false)
    private CategoryEntity categoryEntity;

    // @Lob and @JdbcTypeCode(VARCHAR) contradicted each other: the type code won and turned this into
    // varchar(255), so any longer reason failed on insert. LONGVARCHAR maps to text on Postgres.
    @Column(name = "reason")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server")
    private ServerEntity server;

    @Column(name = "location", nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus reportStatus;

    // A freshly filed report has no handler yet - optional = false together with nullable = false
    // forbade exactly the normal case.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_player_uuid")
    private PlayerEntity handledBy;

    @Column(name = "handler_note")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String handlerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screenshot")
    private ScreenshotEntity screenshotEntity;

    // Previously a final field with an initialiser that Hibernate had to overwrite reflectively on
    // every load. @CreationTimestamp sets the value while persisting, updatable = false protects it.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // @PreUpdate without @PrePersist left updatedAt null until the first change.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Report updates run as read-modify-save across two HTTP calls; without a version the last
    // writer silently wins.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
