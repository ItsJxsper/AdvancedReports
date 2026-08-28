package de.itsjxsper.advancedreports.backend.server.service;

import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.backend.server.data.repository.ServerRepository;
import de.itsjxsper.advancedreports.backend.server.exceptions.ServerNotFoundException;
import de.itsjxsper.advancedreports.backend.server.mapper.ServerMapper;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerMapper serverMapper;

    @Transactional
    public ServerDto createServer(ServerDto serverDto) {
        log.debug("Creating server with ipAddress={}", serverDto.ipAddress());

        ServerEntity serverEntity = serverMapper.toEntity(serverDto);
        ServerEntity savedEntity = this.serverRepository.save(serverEntity);
        log.debug("Created server with ipAddress={}", savedEntity.getServerUuid());
        return serverMapper.toDto(savedEntity);
    }

    public ServerDto getServerByUUID(UUID serverUUID) {
        log.debug("Getting server with serverUUID={}", serverUUID);

        ServerEntity serverEntity = this.serverRepository.findById(serverUUID)
                .orElseThrow(() -> new ServerNotFoundException(serverUUID));

        log.debug("Got server with serverUUID={}", serverEntity.getServerUuid());
        return serverMapper.toDto(serverEntity);
    }

    @Transactional
    public Page<ServerDto> getAllServers(int page, int size) {
        log.debug("Getting all servers with page={} and size={}", page, size);

        Page<ServerEntity> serverEntities = this.serverRepository.findAllByOrderByServerUuidAsc(PageRequest.of(page, size));

        log.debug("Got {} servers", serverEntities.getTotalElements());
        return serverEntities.map(serverMapper::toDto);
    }

    @Transactional
    public ServerDto updateServer(ServerDto serverDto) {
        log.debug("Updating server with serverUUID={}", serverDto.serverUUID());

        ServerEntity serverEntity = this.serverRepository.findById(serverDto.serverUUID())
                .orElseThrow(() -> new ServerNotFoundException(serverDto.serverUUID()));

        serverEntity = this.serverMapper.partialUpdate(serverDto, serverEntity);

        ServerEntity savedEntity = this.serverRepository.save(serverEntity);
        log.debug("Updated server with serverUUID={}", savedEntity.getServerUuid());
        return serverMapper.toDto(savedEntity);
    }

    public void deleteServer(UUID serverUUID) {
        log.debug("Deleting server with serverUUID={}", serverUUID);
        this.serverRepository.deleteById(serverUUID);
        log.debug("Deleted server with serverUUID={}", serverUUID);
    }

    public long countServers() {
        return this.serverRepository.count();
    }


    public long countReportsForServer(UUID serverUUID) {
        // Ohne Existenzpruefung liefert eine unbekannte UUID 0 - nicht unterscheidbar von einem
        // registrierten Server ohne Reports.
        if (!this.serverRepository.existsById(serverUUID)) {
            throw new ServerNotFoundException(serverUUID);
        }

        return this.serverRepository.countReportsByServerUuid(serverUUID);
    }

}
