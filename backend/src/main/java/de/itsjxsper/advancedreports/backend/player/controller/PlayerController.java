package de.itsjxsper.advancedreports.backend.player.controller;

import de.itsjxsper.advancedreports.backend.player.service.PlayerService;
import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import de.itsjxsper.advancedreports.common.model.player.PlayerUpdateDTO;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/player")
@RequiredArgsConstructor
@Tag(name = "Player API", description = "Player API")
public class PlayerController {

    private final PlayerService playerService;

    @GetMapping
    @RateLimited
    @Operation(summary = "Get all players", description = "Retrieve a list of all players")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "404", description = "Players not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<PlayerDTO>> getAllPlayers(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "100")
            @RequestParam(defaultValue = "100") int size
    ) {
        log.debug("Getting all players");

        Page<PlayerDTO> players = this.playerService.getPlayers(PageRequest.of(page, size));

        log.debug("Found {} players", players.getTotalElements());
        return ResponseEntity.ok(players);
    }

    @PostMapping
    @RateLimited
    @Operation(summary = "Create a new player", description = "Create a new player")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Player created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Player already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PlayerDTO> createPlayer(@Valid @RequestBody PlayerUpdateDTO playerUpdateDTO) {
        log.debug("Creating player with uuid={} and name={}", playerUpdateDTO.playerUuid(), playerUpdateDTO.playerName());
        PlayerDTO createdPlayer = this.playerService.createPlayer(playerUpdateDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlayer);
    }

    @PatchMapping
    @RateLimited
    @Operation(summary = "Update an existing player", description = "Update an existing player")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Player updated successfully"),
            @ApiResponse(responseCode = "404", description = "Player not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PlayerDTO> updatePlayer(@Valid @RequestBody PlayerUpdateDTO playerUpdateDTO) {
        log.debug("Updating player with uuid={} and name={}", playerUpdateDTO.playerUuid(), playerUpdateDTO.playerName());
        PlayerDTO updatedPlayer = this.playerService.updatePlayer(playerUpdateDTO);
        return ResponseEntity.ok(updatedPlayer);
    }

    @DeleteMapping("/{playerUuid}")
    @RateLimited
    @Operation(summary = "Delete player by UUID", description = "Delete a player by UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Player deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deletePlayerByUUID(@PathVariable UUID playerUuid) {
        log.debug("Deleting player with uuid={}", playerUuid);
        this.playerService.deletePlayer(playerUuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{playerUuid}")
    @RateLimited
    @Operation(summary = "Get player by UUID", description = "Retrieve player information by UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Player found successfully"),
            @ApiResponse(responseCode = "404", description = "Player not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PlayerDTO> getPlayerByUUID(@PathVariable UUID playerUuid) {
        log.debug("Retrieving player with uuid={}", playerUuid);
        PlayerDTO player = this.playerService.getPlayer(playerUuid);
        if (player == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(player);
    }

    @GetMapping("/count")
    @RateLimited
    @Operation(summary = "Get player count", description = "Retrieve the total number of players")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Player count retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Long> getPlayerCount() {
        log.debug("Retrieving player count");
        long count = this.playerService.countPlayers();
        return ResponseEntity.ok(count);
    }
}
