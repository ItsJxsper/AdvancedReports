package de.itsjxsper.advancedreports.backend.player.service;

import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerAlreadyExistException;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import de.itsjxsper.advancedreports.common.model.player.PlayerUpdateDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerService")
class PlayerServiceTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    private PlayerEntity playerEntity;

    @BeforeEach
    void setUp() {
        playerEntity = TestDataFactory.player(PLAYER_UUID, "Notch");
    }

    @Nested
    @DisplayName("createPlayer")
    class CreatePlayer {

        @Test
        @DisplayName("legt einen neuen Spieler an, wenn die UUID noch nicht existiert")
        void shouldCreatePlayer() {
            PlayerUpdateDTO dto = TestDataFactory.playerUpdateDto(PLAYER_UUID, "Notch");
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.empty());
            when(playerRepository.save(any(PlayerEntity.class))).thenReturn(playerEntity);

            PlayerDTO result = playerService.createPlayer(dto);

            assertThat(result.playerUUID()).isEqualTo(PLAYER_UUID);
            assertThat(result.playerName()).isEqualTo("Notch");

            ArgumentCaptor<PlayerEntity> saved = ArgumentCaptor.forClass(PlayerEntity.class);
            verify(playerRepository).save(saved.capture());
            assertThat(saved.getValue().getPlayerUuid()).isEqualTo(PLAYER_UUID);
            assertThat(saved.getValue().getPlayerName()).isEqualTo("Notch");
        }

        @Test
        @DisplayName("wirft PlayerAlreadyExistException, wenn die UUID bereits existiert")
        void shouldThrowWhenPlayerAlreadyExists() {
            PlayerUpdateDTO dto = TestDataFactory.playerUpdateDto(PLAYER_UUID, "Notch");
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.of(playerEntity));

            assertThatThrownBy(() -> playerService.createPlayer(dto))
                    .isInstanceOf(PlayerAlreadyExistException.class)
                    .hasMessageContaining(PLAYER_UUID.toString());

            verify(playerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updatePlayer")
    class UpdatePlayer {

        @Test
        @DisplayName("aktualisiert den Namen eines bestehenden Spielers")
        void shouldUpdatePlayerName() {
            PlayerUpdateDTO dto = TestDataFactory.playerUpdateDto(PLAYER_UUID, "Jeb_");
            PlayerEntity renamed = TestDataFactory.player(PLAYER_UUID, "Jeb_");

            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.of(playerEntity));
            when(playerRepository.save(playerEntity)).thenReturn(renamed);

            PlayerDTO result = playerService.updatePlayer(dto);

            assertThat(result.playerName()).isEqualTo("Jeb_");
            assertThat(playerEntity.getPlayerName()).isEqualTo("Jeb_");
        }

        @Test
        @DisplayName("wirft PlayerNotFoundException, wenn der Spieler nicht existiert")
        void shouldThrowWhenPlayerNotFound() {
            PlayerUpdateDTO dto = TestDataFactory.playerUpdateDto(PLAYER_UUID, "Jeb_");
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playerService.updatePlayer(dto))
                    .isInstanceOf(PlayerNotFoundException.class);

            verify(playerRepository, never()).save(any());
        }

        @Test
        @DisplayName("lässt den bestehenden Namen unverändert, wenn kein Name mitgeschickt wird")
        void shouldKeepExistingNameWhenNameAbsent() {
            PlayerUpdateDTO dto = new PlayerUpdateDTO(PLAYER_UUID, Optional.empty());

            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.of(playerEntity));
            when(playerRepository.save(playerEntity)).thenReturn(playerEntity);

            playerService.updatePlayer(dto);

            assertThat(playerEntity.getPlayerName()).isEqualTo("Notch");
        }
    }

    @Nested
    @DisplayName("deletePlayer")
    class DeletePlayer {

        @Test
        @DisplayName("löscht einen bestehenden Spieler")
        void shouldDeletePlayer() {
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.of(playerEntity));

            playerService.deletePlayer(PLAYER_UUID);

            verify(playerRepository).delete(playerEntity);
        }

        @Test
        @DisplayName("wirft PlayerNotFoundException, wenn der Spieler nicht existiert")
        void shouldThrowWhenPlayerNotFound() {
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playerService.deletePlayer(PLAYER_UUID))
                    .isInstanceOf(PlayerNotFoundException.class);

            verify(playerRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("getPlayer")
    class GetPlayer {

        @Test
        @DisplayName("liefert den Spieler zur UUID zurück")
        void shouldReturnPlayer() {
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.of(playerEntity));

            PlayerDTO result = playerService.getPlayer(PLAYER_UUID);

            assertThat(result).isEqualTo(new PlayerDTO(PLAYER_UUID, "Notch"));
        }

        @Test
        @DisplayName("wirft PlayerNotFoundException, wenn der Spieler nicht existiert")
        void shouldThrowWhenPlayerNotFound() {
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playerService.getPlayer(PLAYER_UUID))
                    .isInstanceOf(PlayerNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("countPlayers und getPlayers")
    class CountAndList {

        @Test
        @DisplayName("gibt die Gesamtanzahl der Spieler zurück")
        void shouldCountPlayers() {
            when(playerRepository.count()).thenReturn(42L);

            assertThat(playerService.countPlayers()).isEqualTo(42L);
        }

        @Test
        @DisplayName("liefert eine paginierte Liste von Spielern zurück")
        void shouldReturnPagedPlayers() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<PlayerEntity> page = new PageImpl<>(List.of(playerEntity));
            when(playerRepository.findAll(pageable)).thenReturn(page);

            Page<PlayerDTO> result = playerService.getPlayers(pageable);

            assertThat(result.getContent()).containsExactly(new PlayerDTO(PLAYER_UUID, "Notch"));
        }

        @Test
        @DisplayName("liefert eine leere Seite, wenn keine Spieler existieren")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(playerRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

            assertThat(playerService.getPlayers(pageable).getContent()).isEmpty();
        }
    }
}
