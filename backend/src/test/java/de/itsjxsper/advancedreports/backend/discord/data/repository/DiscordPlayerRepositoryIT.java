package de.itsjxsper.advancedreports.backend.discord.data.repository;

import de.itsjxsper.advancedreports.backend.discord.data.entity.DiscordPlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.support.AbstractRepositoryIT;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
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
        @DisplayName("finds the link by the player UUID")
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
        @DisplayName("returns an empty Optional for an unknown player UUID")
        void shouldReturnEmptyForUnknownPlayer() {
            assertThat(discordPlayerRepository.findByPlayerEntity_PlayerUuid(UUID.randomUUID()))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("allows only one link per player")
        void shouldEnforceOneLinkPerPlayer() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 17L));

            assertThatThrownBy(() ->
                    discordPlayerRepository.saveAndFlush(TestDataFactory.discordPlayer(player, 18L)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("persists a real 18-digit Discord snowflake in the database")
        void shouldPersistRealSnowflake() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));

            DiscordPlayerEntity saved =
                    entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 217476470391308288L));
            entityManager.clear();

            assertThat(discordPlayerRepository.findById(saved.getId()))
                    .get()
                    .satisfies(link -> assertThat(link.getDiscordUserId()).isEqualTo(217476470391308288L));
        }
    }

    @Nested
    @DisplayName("Deleting")
    class Deleting {

        @Test
        @DisplayName("deletes only the link, not the player")
        void shouldNotDeletePlayerWithLink() {
            PlayerEntity player = entityManager.persist(TestDataFactory.player(PLAYER_UUID, "Notch"));
            DiscordPlayerEntity link =
                    entityManager.persistAndFlush(TestDataFactory.discordPlayer(player, 17L));

            discordPlayerRepository.delete(link);
            entityManager.flush();
            entityManager.clear();

            assertThat(discordPlayerRepository.findById(link.getId())).isEmpty();
            assertThat(entityManager.find(PlayerEntity.class, PLAYER_UUID))
                    .as("The player has to survive the deletion of their Discord link")
                    .isNotNull();
        }

    }
}
