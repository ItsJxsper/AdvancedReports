package de.itsjxsper.advancedreports.player.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "player", schema = "advancedreports")
public class PlayerEntity {

    @Id
    @Column(name = "player_uuid", nullable = false)
    private UUID playerUUID;

    @Column(name = "player_name", length = 16)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String playerName;
}