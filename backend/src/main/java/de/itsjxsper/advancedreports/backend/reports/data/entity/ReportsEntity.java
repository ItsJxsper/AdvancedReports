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

    // @Lob und @JdbcTypeCode(VARCHAR) widersprachen sich: der Type-Code gewann und machte daraus
    // varchar(255), sodass jeder laengere Grund am Insert scheiterte. LONGVARCHAR ist auf Postgres text.
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

    // Ein frisch eingegangener Report hat noch keinen Bearbeiter - optional = false und
    // nullable = false haben genau den Normalfall verboten.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_player_uuid")
    private PlayerEntity handledBy;

    @Column(name = "handler_note")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String handlerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screenshot")
    private ScreenshotEntity screenshotEntity;

    // Vorher ein final-Feld mit Initialisierer, das Hibernate beim Laden reflektiv ueberschreiben
    // musste. @CreationTimestamp setzt den Wert beim Persistieren, updatable = false schuetzt ihn.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // @PreUpdate ohne @PrePersist liess updatedAt bis zur ersten Aenderung null.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Report-Updates laufen als Lesen-Aendern-Speichern ueber zwei HTTP-Aufrufe; ohne Version
    // gewinnt stillschweigend der letzte Schreiber.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
