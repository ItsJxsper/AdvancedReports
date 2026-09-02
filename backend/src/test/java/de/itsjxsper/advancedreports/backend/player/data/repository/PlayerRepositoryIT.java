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
    @DisplayName("Schema-Mapping")
    class SchemaMapping {

        @Test
        @DisplayName("liegt als Tabelle player im Standard-Schema")
        void shouldLiveInTheDefaultSchema() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.clear();

            // PlayerEntity war einmal das einzige Entity mit einem eigenen Schema
            // (@Table(schema = "advancedreports")), waehrend die anderen fuenf in "public" lagen.
            // Genau das erzwang hibernate.hbm2ddl.create_namespaces in den Test-Properties; seit
            // der Korrektur der Entity-Mappings liegen alle sechs Tabellen im Standard-Schema und
            // die Property ist dort verschwunden.
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
        @DisplayName("findet einen Spieler über seine UUID")
        void shouldFindByUuid() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.clear();

            assertThat(playerRepository.findByPlayerUuid(PLAYER_UUID))
                    .isPresent()
                    .get()
                    .satisfies(player -> assertThat(player.getPlayerName()).isEqualTo("Notch"));
        }

        @Test
        @DisplayName("liefert ein leeres Optional für eine unbekannte UUID")
        void shouldReturnEmptyForUnknownUuid() {
            assertThat(playerRepository.findByPlayerUuid(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("lehnt einen Spieler ohne Namen ab, weil die Spalte nicht nullable ist")
        void shouldRejectPlayerWithoutName() {
            PlayerEntity player = TestDataFactory.player(PLAYER_UUID, null);

            assertThatThrownBy(() -> playerRepository.saveAndFlush(player))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("lehnt einen Namen mit mehr als 16 Zeichen ab")
        void shouldRejectTooLongName() {
            PlayerEntity player = TestDataFactory.player(PLAYER_UUID, "EinVielZuLangerSpielername");

            assertThatThrownBy(() -> playerRepository.saveAndFlush(player))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("überschreibt einen Spieler mit gleicher UUID statt ihn zu duplizieren")
        void shouldTreatUuidAsPrimaryKey() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));
            entityManager.clear();

            // Die UUID ist der Primärschlüssel und wird zugewiesen, nicht generiert - ein save() mit
            // derselben UUID ist damit ein Update.
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
        @DisplayName("löscht den Spieler zur UUID")
        void shouldDeleteByUuid() {
            entityManager.persistAndFlush(TestDataFactory.player(PLAYER_UUID, "Notch"));

            playerRepository.deleteByPlayerUuid(PLAYER_UUID);
            entityManager.flush();
            entityManager.clear();

            assertThat(playerRepository.findByPlayerUuid(PLAYER_UUID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll und count")
    class ListOperations {

        @Test
        @DisplayName("liefert Spieler paginiert und meldet die Gesamtanzahl")
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
