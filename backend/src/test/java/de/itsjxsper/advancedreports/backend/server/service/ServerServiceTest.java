package de.itsjxsper.advancedreports.backend.server.service;

import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.backend.server.data.repository.ServerRepository;
import de.itsjxsper.advancedreports.backend.server.exceptions.ServerNotFoundException;
import de.itsjxsper.advancedreports.backend.server.mapper.ServerMapper;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServerService")
class ServerServiceTest {

    private static final UUID SERVER_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private ServerMapper serverMapper;

    @InjectMocks
    private ServerService serverService;

    private ServerEntity serverEntity;
    private ServerDto serverDto;

    @BeforeEach
    void setUp() {
        serverEntity = TestDataFactory.server();
        serverEntity.setServerUuid(SERVER_UUID);
        serverDto = TestDataFactory.serverDto(SERVER_UUID);
    }

    @Nested
    @DisplayName("createServer")
    class CreateServer {

        @Test
        @DisplayName("registers a new server")
        void shouldCreateServer() {
            when(serverMapper.toEntity(serverDto)).thenReturn(serverEntity);
            when(serverRepository.save(serverEntity)).thenReturn(serverEntity);
            when(serverMapper.toDto(serverEntity)).thenReturn(serverDto);

            ServerDto result = serverService.createServer(serverDto);

            assertThat(result).isEqualTo(serverDto);
            verify(serverRepository).save(serverEntity);
        }
    }

    @Nested
    @DisplayName("getServerByUUID")
    class GetServerByUuid {

        @Test
        @DisplayName("returns the server for the UUID")
        void shouldReturnServer() {
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
            when(serverMapper.toDto(serverEntity)).thenReturn(serverDto);

            assertThat(serverService.getServerByUUID(SERVER_UUID)).isEqualTo(serverDto);
        }

        @Test
        @DisplayName("throws ServerNotFoundException when the server does not exist")
        void shouldThrowWhenServerNotFound() {
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> serverService.getServerByUUID(SERVER_UUID))
                    .isInstanceOf(ServerNotFoundException.class)
                    .hasMessageContaining(SERVER_UUID.toString());
        }
    }

    @Nested
    @DisplayName("getAllServers")
    class GetAllServers {

        @Test
        @DisplayName("returns a paginated list sorted by UUID")
        void shouldReturnPagedServers() {
            when(serverRepository.findAllByOrderByServerUuidAsc(PageRequest.of(0, 10)))
                    .thenReturn(new PageImpl<>(List.of(serverEntity)));
            when(serverMapper.toDto(serverEntity)).thenReturn(serverDto);

            Page<ServerDto> result = serverService.getAllServers(0, 10);

            assertThat(result.getContent()).containsExactly(serverDto);
            verify(serverRepository).findAllByOrderByServerUuidAsc(PageRequest.of(0, 10));
        }
    }

    @Nested
    @DisplayName("updateServer")
    class UpdateServer {

        @Test
        @DisplayName("updates an existing server")
        void shouldUpdateServer() {
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
            when(serverMapper.partialUpdate(serverDto, serverEntity)).thenReturn(serverEntity);
            when(serverRepository.save(serverEntity)).thenReturn(serverEntity);
            when(serverMapper.toDto(serverEntity)).thenReturn(serverDto);

            assertThat(serverService.updateServer(serverDto)).isEqualTo(serverDto);
            verify(serverRepository).save(serverEntity);
        }

        @Test
        @DisplayName("throws ServerNotFoundException when the server does not exist")
        void shouldThrowWhenServerNotFound() {
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> serverService.updateServer(serverDto))
                    .isInstanceOf(ServerNotFoundException.class);

            verify(serverRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteServer")
    class DeleteServer {

        @Test
        @DisplayName("deletes the server for the UUID")
        void shouldDeleteServer() {
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));

            serverService.deleteServer(SERVER_UUID);

            verify(serverRepository).delete(serverEntity);
        }

        @Test
        @DisplayName("throws ServerNotFoundException when the server does not exist")
        void shouldThrowWhenServerNotFound() {
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> serverService.deleteServer(SERVER_UUID))
                    .isInstanceOf(ServerNotFoundException.class);

            verify(serverRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("countServers and countReportsForServer")
    class CountOperations {

        @Test
        @DisplayName("returns the total number of servers")
        void shouldCountServers() {
            when(serverRepository.count()).thenReturn(3L);

            assertThat(serverService.countServers()).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns the number of reports for a server")
        void shouldCountReportsForServer() {
            when(serverRepository.existsById(SERVER_UUID)).thenReturn(true);
            when(serverRepository.countReportsByServerUuid(SERVER_UUID)).thenReturn(5L);

            assertThat(serverService.countReportsForServer(SERVER_UUID)).isEqualTo(5L);
        }

        @Test
        @DisplayName("throws ServerNotFoundException when the server does not exist")
        void shouldThrowWhenCountingReportsForUnknownServer() {
            when(serverRepository.existsById(SERVER_UUID)).thenReturn(false);

            assertThatThrownBy(() -> serverService.countReportsForServer(SERVER_UUID))
                    .isInstanceOf(ServerNotFoundException.class);

            verify(serverRepository, never()).countReportsByServerUuid(any());
        }
    }
}
