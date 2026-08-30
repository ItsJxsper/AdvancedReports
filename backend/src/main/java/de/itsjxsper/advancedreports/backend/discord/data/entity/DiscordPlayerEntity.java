package de.itsjxsper.advancedreports.backend.discord.data.entity;

import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import lombok.Getter;
import lombok.Setter;

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

    // Discord-Snowflakes sind 17-19-stellig und gehoeren als Long in eine bigint-Spalte.
    @Digits(integer = 19, fraction = 0)
    @Column(name = "discord_user_id")
    private Long discordUserId;

}