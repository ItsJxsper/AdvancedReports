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

    // Die Verknuepfung ist die abhaengige Seite und darf den Spieler nicht besitzen. Mit
    // cascade = ALL + orphanRemoval riss das Loesen einer Discord-Verknuepfung den
    // Minecraft-Spieler mit - und ueber dessen Reports potenziell noch mehr.
    @OneToOne(optional = false)
    @JoinColumn(name = "player_entity_player_uuid", nullable = false, unique = true)
    private PlayerEntity playerEntity;

    // Discord-Snowflakes sind 17-19-stellige Zahlen. @Max(18) hat den *Wert* auf 18 begrenzt und
    // damit jede echte ID abgelehnt; @JdbcTypeCode(LONG32NVARCHAR) hat die Spalte ausserdem auf
    // text gelegt, woraus Hibernate einen numerischen CHECK gegen text generiert hat - Postgres
    // lehnte das DDL ab ("operator does not exist: text <= integer") und die Tabelle entstand nie.
    @Digits(integer = 19, fraction = 0)
    @Column(name = "discord_user_id")
    private Long discordUserId;

}