package de.itsjxsper.advancedreports.backend.discord.service;

import de.itsjxsper.advancedreports.backend.discord.data.entity.DiscordPlayerEntity;
import de.itsjxsper.advancedreports.backend.discord.data.repository.DiscordPlayerRepository;
import de.itsjxsper.advancedreports.backend.discord.exceptions.DiscordUserNotFoundException;
import de.itsjxsper.advancedreports.backend.discord.mapper.DiscordPlayerMapper;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordPlayerService")
class DiscordPlayerServiceTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long DISCORD_PLAYER_ID = 5L;
    private static final Long DISCORD_USER_ID = 17L;

    @Mock
    private DiscordPlayerRepository discordPlayerRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private DiscordPlayerMapper discordPlayerMapper;

    @InjectMocks
    private DiscordPlayerService discordPlayerService;

    private PlayerEntity playerEntity;
    private DiscordPlayerEntity discordPlayerEntity;
    private DiscordPlayerDto discordPlayerDto;

    @BeforeEach
    void setUp() {
        playerEntity = TestDataFactory.player(PLAYER_UUID, "Notch");
        discordPlayerEntity = TestDataFactory.discordPlayer(playerEntity, DISCORD_USER_ID);
        discordPlayerEntity.setId(DISCORD_PLAYER_ID);
        discordPlayerDto = new DiscordPlayerDto(DISCORD_PLAYER_ID, PLAYER_UUID, DISCORD_USER_ID);
    }

    @Nested
    @DisplayName("createDiscordPlayer")
    class CreateDiscordPlayer {

        @Test
        @DisplayName("links an existing player to a Discord account")
        void shouldCreateDiscordPlayer() {
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.of(playerEntity));
            when(discordPlayerMapper.toEntity(discordPlayerDto)).thenReturn(discordPlayerEntity);
            when(discordPlayerRepository.save(discordPlayerEntity)).thenReturn(discordPlayerEntity);
            when(discordPlayerMapper.toDto(discordPlayerEntity)).thenReturn(discordPlayerDto);

            DiscordPlayerDto result = discordPlayerService.createDiscordPlayer(discordPlayerDto);

            assertThat(result).isEqualTo(discordPlayerDto);
            assertThat(discordPlayerEntity.getPlayerEntity()).isSameAs(playerEntity);
        }

        @Test
        @DisplayName("throws PlayerNotFoundException when the Minecraft player does not exist")
        void shouldThrowWhenPlayerMissing() {
            when(playerRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> discordPlayerService.createDiscordPlayer(discordPlayerDto))
                    .isInstanceOf(PlayerNotFoundException.class)
                    .hasMessageContaining(PLAYER_UUID.toString());

            verify(discordPlayerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getDiscordPlayerById")
    class GetById {

        @Test
        @DisplayName("returns the link for the id")
        void shouldReturnDiscordPlayer() {
            when(discordPlayerRepository.findById(DISCORD_PLAYER_ID))
                    .thenReturn(Optional.of(discordPlayerEntity));
            when(discordPlayerMapper.toDto(discordPlayerEntity)).thenReturn(discordPlayerDto);

            assertThat(discordPlayerService.getDiscordPlayerById(DISCORD_PLAYER_ID))
                    .isEqualTo(discordPlayerDto);
        }

        @Test
        @DisplayName("throws DiscordUserNotFoundException when the id is unknown")
        void shouldThrowWhenNotFound() {
            when(discordPlayerRepository.findById(DISCORD_PLAYER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> discordPlayerService.getDiscordPlayerById(DISCORD_PLAYER_ID))
                    .isInstanceOf(DiscordUserNotFoundException.class)
                    .hasMessageContaining(String.valueOf(DISCORD_PLAYER_ID));
        }
    }

    @Nested
    @DisplayName("getDiscordPlayerByPlayerUUID")
    class GetByPlayerUuid {

        @Test
        @DisplayName("returns the link for the player UUID")
        void shouldReturnDiscordPlayer() {
            when(discordPlayerRepository.findByPlayerEntity_PlayerUuid(PLAYER_UUID))
                    .thenReturn(Optional.of(discordPlayerEntity));
            when(discordPlayerMapper.toDto(discordPlayerEntity)).thenReturn(discordPlayerDto);

            assertThat(discordPlayerService.getDiscordPlayerByPlayerUUID(PLAYER_UUID))
                    .isEqualTo(discordPlayerDto);
        }

        @Test
        @DisplayName("throws DiscordUserNotFoundException when the player UUID is unknown")
        void shouldThrowWhenNotFound() {
            when(discordPlayerRepository.findByPlayerEntity_PlayerUuid(PLAYER_UUID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> discordPlayerService.getDiscordPlayerByPlayerUUID(PLAYER_UUID))
                    .isInstanceOf(DiscordUserNotFoundException.class)
                    .hasMessageContaining(PLAYER_UUID.toString());
        }
    }

    @Nested
    @DisplayName("updateDiscordPlayer")
    class UpdateDiscordPlayer {

        @Test
        @DisplayName("updates the Discord user id")
        void shouldUpdateDiscordUserId() {
            DiscordPlayerDto update = new DiscordPlayerDto(DISCORD_PLAYER_ID, PLAYER_UUID, 18L);

            when(discordPlayerRepository.findById(DISCORD_PLAYER_ID))
                    .thenReturn(Optional.of(discordPlayerEntity));
            when(discordPlayerRepository.save(discordPlayerEntity)).thenReturn(discordPlayerEntity);
            when(discordPlayerMapper.toDto(discordPlayerEntity)).thenReturn(update);

            assertThat(discordPlayerService.updateDiscordPlayer(update)).isEqualTo(update);
            assertThat(discordPlayerEntity.getDiscordUserId()).isEqualTo(18L);
        }

        @Test
        @DisplayName("leaves the Discord user id unchanged when it is null")
        void shouldKeepDiscordUserIdWhenNull() {
            DiscordPlayerDto update = new DiscordPlayerDto(DISCORD_PLAYER_ID, PLAYER_UUID, null);

            when(discordPlayerRepository.findById(DISCORD_PLAYER_ID))
                    .thenReturn(Optional.of(discordPlayerEntity));
            when(discordPlayerRepository.save(discordPlayerEntity)).thenReturn(discordPlayerEntity);
            when(discordPlayerMapper.toDto(discordPlayerEntity)).thenReturn(discordPlayerDto);

            discordPlayerService.updateDiscordPlayer(update);

            assertThat(discordPlayerEntity.getDiscordUserId()).isEqualTo(DISCORD_USER_ID);
        }

        @Test
        @DisplayName("throws DiscordUserNotFoundException when the link does not exist")
        void shouldThrowWhenNotFound() {
            when(discordPlayerRepository.findById(DISCORD_PLAYER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> discordPlayerService.updateDiscordPlayer(discordPlayerDto))
                    .isInstanceOf(DiscordUserNotFoundException.class);

            verify(discordPlayerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteDiscordPlayerByDiscordId")
    class DeleteById {

        @Test
        @DisplayName("deletes the link for the id")
        void shouldDelete() {
            when(discordPlayerRepository.findById(DISCORD_PLAYER_ID))
                    .thenReturn(Optional.of(discordPlayerEntity));

            discordPlayerService.deleteDiscordPlayerByDiscordId(DISCORD_PLAYER_ID);

            verify(discordPlayerRepository).delete(discordPlayerEntity);
        }

        @Test
        @DisplayName("throws DiscordUserNotFoundException when the id is unknown")
        void shouldThrowWhenNotFound() {
            when(discordPlayerRepository.findById(DISCORD_PLAYER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> discordPlayerService.deleteDiscordPlayerByDiscordId(DISCORD_PLAYER_ID))
                    .isInstanceOf(DiscordUserNotFoundException.class);

            verify(discordPlayerRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("deleteDiscordPlayerByPlayerUUID")
    class DeleteByPlayerUuid {

        @Test
        @DisplayName("deletes the link for the player UUID")
        void shouldDelete() {
            when(discordPlayerRepository.findByPlayerEntity_PlayerUuid(PLAYER_UUID))
                    .thenReturn(Optional.of(discordPlayerEntity));

            discordPlayerService.deleteDiscordPlayerByPlayerUUID(PLAYER_UUID);

            verify(discordPlayerRepository).delete(discordPlayerEntity);
        }

        @Test
        @DisplayName("throws DiscordUserNotFoundException when the player UUID is unknown")
        void shouldThrowWhenNotFound() {
            when(discordPlayerRepository.findByPlayerEntity_PlayerUuid(PLAYER_UUID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> discordPlayerService.deleteDiscordPlayerByPlayerUUID(PLAYER_UUID))
                    .isInstanceOf(DiscordUserNotFoundException.class);

            verify(discordPlayerRepository, never()).delete(any());
        }
    }
}
