package de.itsjxsper.advancedreports.backend.player.data.repository;

import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.support.AbstractRepositoryIT;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlayerRepository")
class PlayerRepositoryIT extends AbstractRepositoryIT {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Nested
    @DisplayName("Schema mapping")
    class SchemaMapping {

        @Test
        @DisplayName("lives as table player in the default schema")
        void shouldLiveInTheDefaultSchema() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.clear();

            // PlayerEntity was once the only entity with a schema of its own
            // (@Table(schema = "advancedreports")), while the other five lived in "public".
            // That is exactly what forced hibernate.hbm2ddl.create_namespaces in the test properties;
            // since the entity mappings were corrected all six tables live in the default schema and
            // the property is gone from there.
            Object count = entityManager.getEntityManager()
                    .createNativeQuery("select count(*) from player")
                    .getSingleResult();

            assertThat(((Number) count).intValue()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByPlayerUuid")
    class FindByPlayerUuid {

        @Test
        @DisplayName("finds a player by their UUID")
        void shouldFindByUuid() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.clear();

            assertThat(playerRepository.findByPlayerUuid(PLAYER_UUID))
                    .isPresent()
                    .get()
                    .satisfies(player -> assertThat(player.getPlayerName()).isEqualTo("Notch"));
        }

        @Test
        @DisplayName("returns an empty Optional for an unknown UUID")
        void shouldReturnEmptyForUnknownUuid() {
            assertThat(playerRepository.findByPlayerUuid(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("rejects a player without a name because the column is not nullable")
        void shouldRejectPlayerWithoutName() {
            PlayerEntity player = TestDataFactory.player(PLAYER_UUID, null);

            assertThatThrownBy(() -> playerRepository.saveAndFlush(player))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("rejects a name longer than 16 characters")
        void shouldRejectTooLongName() {
            PlayerEntity player = TestDataFactory.player(PLAYER_UUID, "EinVielZuLangerSpielername");

            assertThatThrownBy(() -> playerRepository.saveAndFlush(player))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("overwrites a player with the same UUID instead of duplicating them")
        void shouldTreatUuidAsPrimaryKey() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.clear();

            // The UUID is the primary key and is assigned, not generated - so a save() with the same
            // UUID is an update.
            playerRepository.save(TestDataFactory.player(PLAYER_UUID, "Jeb_"));
            entityManager.flush();
            entityManager.clear();

            assertThat(playerRepository.count()).isEqualTo(1);
            assertThat(playerRepository.findByPlayerUuid(PLAYER_UUID))
                    .get()
                    .satisfies(player -> assertThat(player.getPlayerName()).isEqualTo("Jeb_"));
        }
    }

    @Nested
    @DisplayName("deleteByPlayerUuid")
    class DeleteByPlayerUuid {

        @Test
        @DisplayName("deletes the player for the UUID")
        void shouldDeleteByUuid() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));

            playerRepository.deleteByPlayerUuid(PLAYER_UUID);
            entityManager.flush();
            entityManager.clear();

            assertThat(playerRepository.findByPlayerUuid(PLAYER_UUID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll and count")
    class ListOperations {

        @Test
        @DisplayName("returns players with pagination and reports the total count")
        void shouldPaginate() {
            entityManager.persist(TestDataFactory.player("Notch"));
            entityManager.persist(TestDataFactory.player("Jeb"));
            entityManager.persist(TestDataFactory.player("Dinnerbone"));
            entityManager.flush();
            entityManager.clear();

            var page = playerRepository.findAll(PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
        }
    }
}
