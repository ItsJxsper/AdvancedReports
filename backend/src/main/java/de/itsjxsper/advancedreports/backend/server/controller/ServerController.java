package de.itsjxsper.advancedreports.backend.server.controller;

import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.backend.server.service.ServerService;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/servers")
@Tag(name = "Server API", description = "Server API")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    @PostMapping
    @RateLimited
    // Die UUID kommt vom Client, save() wird damit zu einem merge: registriert sich ein Server
    // erneut, aktualisiert das seine IP und seinen Port, statt zu kollidieren.
    @Operation(summary = "Register server",
            description = "Register a Minecraft server under its own UUID. Registering an already "
                    + "known UUID updates that server instead of creating a second one.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Server registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ServerDto> createServer(@Valid @RequestBody ServerDto serverDto) {
        log.debug("Creating server with serverUUID={}", serverDto.serverUUID());

        var createdServer = this.serverService.createServer(serverDto);

        log.debug("Created server with serverUUID={}", createdServer.serverUUID());

        return ResponseEntity.status(HttpStatus.CREATED).body(createdServer);
    }

    @GetMapping("/{serverUUID}")
    @RateLimited
    @Operation(summary = "Get server by UUID", description = "Retrieve a server by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Server found successfully"),
            @ApiResponse(responseCode = "404", description = "Server not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ServerDto> getServerByUUID(@PathVariable UUID serverUUID) {
        log.debug("Getting server with serverUUID={}", serverUUID);

        var server = this.serverService.getServerByUUID(serverUUID);

        log.debug("Got server with serverUUID={}", server.serverUUID());

        return ResponseEntity.ok(server);
    }

    @GetMapping
    @RateLimited
    @Operation(summary = "Get all servers", description = "Retrieve a paginated list of servers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<ServerDto>> getAllServers(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        log.debug("Getting all servers with page={} and size={}", page, size);

        var servers = this.serverService.getAllServers(page, size);

        log.debug("Got {} servers", servers.getTotalElements());

        return ResponseEntity.ok(servers);
    }

    @PatchMapping
    @RateLimited
    @Operation(summary = "Update server", description = "Update an existing server, identified by the UUID in the body")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Server updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Server not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ServerDto> updateServer(@Valid @RequestBody ServerDto serverDto) {
        log.debug("Updating server with serverUUID={}", serverDto.serverUUID());

        var updatedServer = this.serverService.updateServer(serverDto);

        log.debug("Updated server with serverUUID={}", updatedServer.serverUUID());

        return ResponseEntity.ok(updatedServer);
    }

    @DeleteMapping("/{serverUUID}")
    @RateLimited
    @Operation(summary = "Delete server", description = "Remove a server by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Server deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Server not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteServer(@PathVariable UUID serverUUID) {
        log.debug("Deleting server with serverUUID={}", serverUUID);
        this.serverService.deleteServer(serverUUID);
        log.debug("Deleted server with serverUUID={}", serverUUID);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    @RateLimited
    @Operation(summary = "Get server count", description = "Retrieve the total number of registered servers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Server count retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Long> getServerCount() {
        log.debug("Retrieving server count");
        long count = this.serverService.countServers();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{serverUUID}/reports/count")
    @RateLimited
    @Operation(summary = "Get report count for server", description = "Retrieve the number of reports for a server")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report count retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Server not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Long> getReportCountForServer(@PathVariable UUID serverUUID) {
        log.debug("Retrieving report count for server with serverUUID={}", serverUUID);
        long count = this.serverService.countReportsForServer(serverUUID);
        return ResponseEntity.ok(count);
    }
}
