package de.itsjxsper.advancedreports.backend.reports.data.entity;

import de.itsjxsper.advancedreports.backend.categories.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.reports.eums.Status;
import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "reports_entity")
public class ReportsEntity {
    @Column(name = "created_at")
    private final Instant createdAt = Instant.now();
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_uuid", nullable = false)
    private PlayerEntity reporter;
    @ManyToOne(optional = false)
    @JoinColumn(name = "reported_uuid", nullable = false)
    private PlayerEntity reported;
    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private CategoryEntity categoryEntity;
    @Lob
    @Column(name = "reason")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String reason;
    @ManyToOne
    @JoinColumn(name = "server")
    private ServerEntity server;
    @Column(name = "location", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String location;
    @Column(name = "status", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Status status;
    @ManyToOne(optional = false)
    @JoinColumn(name = "handled_by_player_uuid", nullable = false)
    private PlayerEntity handledBy;
    @Lob
    @Column(name = "handler_note")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String handlerNote;
    @ManyToOne
    @JoinColumn(name = "screenshot")
    private ScreenshotEntity screenshotEntity;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}