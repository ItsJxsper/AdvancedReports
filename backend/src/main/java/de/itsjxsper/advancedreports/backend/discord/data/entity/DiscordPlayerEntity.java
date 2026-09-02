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

    // The link is the dependent side and must not own the player. With cascade = ALL +
    // orphanRemoval, unlinking a Discord account dragged the Minecraft player along with it -
    // and through that player's reports potentially far more.
    @OneToOne(optional = false)
    @JoinColumn(name = "player_entity_player_uuid", nullable = false, unique = true)
    private PlayerEntity playerEntity;

    // Discord snowflakes are 17-19 digit numbers. @Max(18) capped the *value* at 18 and therefore
    // rejected every real ID; @JdbcTypeCode(LONG32NVARCHAR) also put the column on text, from which
    // Hibernate generated a numeric CHECK against text - Postgres refused that DDL
    // ("operator does not exist: text <= integer") and the table was never created.
    @Digits(integer = 19, fraction = 0)
    @Column(name = "discord_user_id")
    private Long discordUserId;

}