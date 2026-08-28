package de.itsjxsper.advancedreports.backend.discord.data.repository;

import de.itsjxsper.advancedreports.backend.discord.data.entity.DiscordPlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.support.AbstractRepositoryIT;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DiscordPlayerRepository")
class DiscordPlayerRepositoryIT extends AbstractRepositoryIT {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private DiscordPlayerRepository discordPlayerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Nested
    @DisplayName("findByPlayerEntity_PlayerUuid")
    class FindByPlayerUuid {

        @Test
        @DisplayName("findet die Verknüpfung über die Spieler-UUID")
        void shouldFindByPlayerUuid() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 17L));
            entityManager.clear();

            assertThat(discordPlayerRepository.findByPlayerEntity_PlayerUuid(PLAYER_UUID))
                    .isPresent()
                    .get()
                    .satisfies(link -> {
                        assertThat(link.getDiscordUserId()).isEqualTo(17L);
                        assertThat(link.getPlayerEntity().getPlayerName()).isEqualTo("Notch");
                    });
        }

        @Test
        @DisplayName("liefert ein leeres Optional für eine unbekannte Spieler-UUID")
        void shouldReturnEmptyForUnknownPlayer() {
            assertThat(discordPlayerRepository.findByPlayerEntity_PlayerUuid(UUID.randomUUID()))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("erlaubt nur eine Verknüpfung pro Spieler")
        void shouldEnforceOneLinkPerPlayer() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 17L));

            assertThatThrownBy(() ->
                    entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 18L)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("speichert eine echte, 18-stellige Discord-Snowflake in der Datenbank")
        void shouldPersistRealSnowflake() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));

            // Auf DB-Ebene passt die Snowflake problemlos - blockiert wird sie erst von der
            // Bean Validation (@Max(18)), siehe DiscordPlayerControllerTest.
            DiscordPlayerEntity saved =
                    entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 217476470391308288L));
            entityManager.clear();

            assertThat(discordPlayerRepository.findById(saved.getId()))
                    .get()
                    .satisfies(link -> assertThat(link.getDiscordUserId()).isEqualTo(217476470391308288L));
        }
    }

    @Nested
    @DisplayName("Löschen")
    class Deleting {

        @Test
        @Disabled("BUG: DiscordPlayerEntity#playerEntity ist mit "
                + "@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true) gemappt "
                + "(discord/data/entity/DiscordPlayerEntity.java:14). Das Loeschen einer "
                + "Discord-Verknuepfung reisst damit den Minecraft-Spieler mit - und ueber dessen "
                + "Reports potenziell weitere Daten. Die Verknuepfung ist die abhaengige Seite und "
                + "darf den Spieler nicht besitzen; erwartet wird cascade = {} ohne orphanRemoval.")
        @DisplayName("löscht nur die Verknüpfung, nicht den Spieler")
        void shouldNotDeletePlayerWithLink() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));
            DiscordPlayerEntity link =
                    entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 17L));

            discordPlayerRepository.delete(link);
            entityManager.flush();
            entityManager.clear();

            assertThat(discordPlayerRepository.findById(link.getId())).isEmpty();
            assertThat(entityManager.find(PlayerEntity.class, PLAYER_UUID))
                    .as("Der Spieler muss die Löschung seiner Discord-Verknüpfung überleben")
                    .isNotNull();
        }

        @Test
        @DisplayName("dokumentiert, dass das Löschen der Verknüpfung aktuell den Spieler mitlöscht")
        void shouldCurrentlyDeletePlayerToo() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));
            DiscordPlayerEntity link =
                    entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 17L));

            discordPlayerRepository.delete(link);
            entityManager.flush();
            entityManager.clear();

            assertThat(entityManager.find(PlayerEntity.class, PLAYER_UUID)).isNull();
        }
    }
}
