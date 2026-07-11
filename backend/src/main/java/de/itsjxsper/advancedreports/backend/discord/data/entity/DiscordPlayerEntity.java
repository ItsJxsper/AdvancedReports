package de.itsjxsper.advancedreports.backend.discord.data.entity;

import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "discord_player_entity")
public class DiscordPlayerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, optional = false, orphanRemoval = true)
    @JoinColumn(name = "player_entity_player_uuid", nullable = false, unique = true)
    private PlayerEntity playerEntity;

    @Max(18)
    @Column(name = "discord_user_id")
    @JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
    private Long discordUserId;

}