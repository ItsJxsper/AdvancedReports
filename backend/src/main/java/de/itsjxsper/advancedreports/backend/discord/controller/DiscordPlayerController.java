package de.itsjxsper.advancedreports.backend.discord.controller;

import de.itsjxsper.advancedreports.backend.discord.service.DiscordPlayerService;
import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/discord-players")
@RequiredArgsConstructor
@Tag(name = "Discord Player API", description = "Discord Player API")
public class DiscordPlayerController {

    private final DiscordPlayerService discordPlayerService;

    @PostMapping
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Create a new Discord Player", description = "Create a new Discord Player entry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Discord Player created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<DiscordPlayerDto> createDiscordPlayer(@Valid @RequestBody DiscordPlayerDto discordPlayerDto) {
        log.debug("Creating Discord Player with playerUUID={}", discordPlayerDto.playerEntityPlayerUUID());
        DiscordPlayerDto createdPlayer = this.discordPlayerService.createDiscordPlayer(discordPlayerDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlayer);
    }

    @GetMapping("/{discordPlayerId}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Get Discord Player by ID", description = "Retrieve a Discord Player by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Discord Player found successfully"),
            @ApiResponse(responseCode = "404", description = "Discord Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<DiscordPlayerDto> getDiscordPlayerById(
            @Parameter(description = "Discord Player ID", example = "1")
            @PathVariable Long discordPlayerId) {
        log.debug("Getting Discord Player with id={}", discordPlayerId);
        DiscordPlayerDto discordPlayer = this.discordPlayerService.getDiscordPlayerById(discordPlayerId);
        return ResponseEntity.ok(discordPlayer);
    }


    @GetMapping("/player/{playerUUID}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Get Discord Player by Player UUID", description = "Retrieve a Discord Player by Player UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Discord Player found successfully"),
            @ApiResponse(responseCode = "404", description = "Discord Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<DiscordPlayerDto> getDiscordPlayerByPlayerUUID(
            @Parameter(description = "Player UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID playerUUID) {
        log.debug("Getting Discord Player for playerUUID={}", playerUUID);
        DiscordPlayerDto discordPlayer = this.discordPlayerService.getDiscordPlayerByPlayerUUID(playerUUID);
        return ResponseEntity.ok(discordPlayer);
    }

    @PutMapping("/{discordPlayerId}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Update Discord Player", description = "Update an existing Discord Player")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Discord Player updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Discord Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<DiscordPlayerDto> updateDiscordPlayer(
            @PathVariable Long discordPlayerId,
            @Valid @RequestBody DiscordPlayerDto discordPlayerDto) {
        log.debug("Updating Discord Player with id={}", discordPlayerId);
        DiscordPlayerDto updatedPlayer = this.discordPlayerService.updateDiscordPlayer(discordPlayerDto);
        return ResponseEntity.ok(updatedPlayer);
    }

    @DeleteMapping("/{discordPlayerId}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Delete Discord Player by ID", description = "Delete a Discord Player by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Discord Player deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Discord Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteDiscordPlayerByDiscordId(
            @Parameter(description = "Discord Player ID", example = "1")
            @PathVariable Long discordPlayerId) {
        log.debug("Deleting Discord Player with id={}", discordPlayerId);
        this.discordPlayerService.deleteDiscordPlayerByDiscordId(discordPlayerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/player/{playerUUID}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Delete Discord Player by Player UUID", description = "Delete a Discord Player by Player UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Discord Player deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Discord Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteDiscordPlayerByPlayerUUID(
            @Parameter(description = "Player UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID playerUUID) {
        log.debug("Deleting Discord Player for playerUUID={}", playerUUID);
        this.discordPlayerService.deleteDiscordPlayerByPlayerUUID(playerUUID);
        return ResponseEntity.noContent().build();
    }
}

