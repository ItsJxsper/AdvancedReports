package de.itsjxsper.advancedreports.backend.server.controller;

import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.backend.server.service.ServerService;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<ServerDto> createServer(ServerDto serverDto) {
        log.debug("Creating server with ipAddress={}", serverDto.ipAddress());

        var createdServer = this.serverService.createServer(serverDto);

        log.debug("Created server with ipAddress={}", createdServer.ipAddress());

        return ResponseEntity.ok(createdServer);
    }

    @GetMapping("/{serverUUID}")
    @RateLimited
    public ResponseEntity<ServerDto> getServerByUUID(@PathVariable UUID serverUUID) {
        log.debug("Getting server with serverUUID={}", serverUUID);

        var server = this.serverService.getServerByUUID(serverUUID);

        log.debug("Got server with serverUUID={}", server.serverUUID());

        return ResponseEntity.ok(server);
    }

    @GetMapping
    @RateLimited
    public ResponseEntity<Iterable<ServerDto>> getAllServers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        log.debug("Getting all servers with page={} and size={}", page, size);

        var servers = this.serverService.getAllServers(page, size);

        log.debug("Got {} servers", servers.getTotalElements());

        return ResponseEntity.ok(servers);
    }

    @PatchMapping
    @RateLimited
    public ResponseEntity<ServerDto> updateServer(ServerDto serverDto) {
        log.debug("Updating server with ipAddress={}", serverDto.ipAddress());

        var updatedServer = this.serverService.updateServer(serverDto);

        log.debug("Updated server with ipAddress={}", updatedServer.ipAddress());

        return ResponseEntity.ok(updatedServer);
    }

    @DeleteMapping("/{serverUUID}")
    @RateLimited
    public ResponseEntity<Void> deleteServer(@PathVariable UUID serverUUID) {
        log.debug("Deleting server with serverUUID={}", serverUUID);
        this.serverService.deleteServer(serverUUID);
        log.debug("Deleted server with serverUUID={}", serverUUID);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    @RateLimited
    public ResponseEntity<Long> getServerCount() {
        log.debug("Retrieving server count");
        long count = this.serverService.countServers();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{serverUUID}/reports/count")
    @RateLimited
    public ResponseEntity<Long> getReportCountForServer(@PathVariable UUID serverUUID) {
        log.debug("Retrieving report count for server with serverUUID={}", serverUUID);
        long count = this.serverService.countReportsForServer(serverUUID);
        return ResponseEntity.ok(count);
    }
}
