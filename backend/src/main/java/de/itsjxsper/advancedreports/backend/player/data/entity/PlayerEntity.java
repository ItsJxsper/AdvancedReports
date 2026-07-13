package de.itsjxsper.advancedreports.backend.player.data.entity;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "player", schema = "advancedreports")
public class PlayerEntity {

    @Id
    @Column(name = "player_uuid", nullable = false)
    private UUID playerUuid;

    @Column(name = "player_name", nullable = false, length = 16)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String playerName;

    @OneToMany(mappedBy = "reporter")
    private Set<ReportsEntity> reportsEntities = new LinkedHashSet<>();

    @OneToMany(mappedBy = "reported")
    private Set<ReportsEntity> reportedEntities = new LinkedHashSet<>();

    @OneToMany(mappedBy = "handledBy")
    private Set<ReportsEntity> handledReports = new LinkedHashSet<>();
}