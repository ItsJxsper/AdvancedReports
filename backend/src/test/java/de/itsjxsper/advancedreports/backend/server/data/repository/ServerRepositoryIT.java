package de.itsjxsper.advancedreports.backend.server.data.repository;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.backend.support.AbstractRepositoryIT;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import org.hibernate.id.IdentifierGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ServerRepository")
class ServerRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PlayerEntity reporter;
    private PlayerEntity reported;
    private PlayerEntity handler;
    private CategoryEntity category;

    @BeforeEach
    void persistReferenceData() {
        reporter = entityManager.persist(TestDataFactory.player("Reporter"));
        reported = entityManager.persist(TestDataFactory.player("Reported"));
        handler = entityManager.persist(TestDataFactory.player("Handler"));
        category = entityManager.persist(TestDataFactory.category("cheating"));
        entityManager.flush();
    }

    private ServerEntity persistServerWithReports(int reportCount) {
        ServerEntity server = entityManager.persist(TestDataFactory.server());

        for (int i = 0; i < reportCount; i++) {
            entityManager.persist(TestDataFactory.report(reporter, reported, handler, category, server));
        }

        entityManager.flush();
        return server;
    }

    @Nested
    @DisplayName("Persisting")
    class Persisting {

        @Test
        @DisplayName("keeps the supplied server UUID unchanged")
        void shouldKeepAssignedServerUuid() {
            UUID serverUuid = UUID.randomUUID();

            ServerEntity saved = entityManager.persistAndFlush(TestDataFactory.server(serverUuid));

            // ServerEntity deliberately has no @GeneratedValue: the Minecraft server registers under
            // its own configured UUID, which ServerDto#serverUUID also sends as @NotNull. A
            // generator would overwrite exactly that UUID while persisting.
            assertThat(saved.getServerUuid()).isEqualTo(serverUuid);
        }

        @Test
        @DisplayName("rejects a server without a UUID because the identifier has to be assigned")
        void shouldRejectServerWithoutUuid() {
            ServerEntity server = TestDataFactory.server();
            server.setServerUuid(null);

            assertThatThrownBy(() -> entityManager.persistAndFlush(server))
                    .isInstanceOf(IdentifierGenerationException.class);
        }

        @Test
        @DisplayName("persists the IP address and the port")
        void shouldPersistAddressAndPort() {
            ServerEntity saved = entityManager.persistAndFlush(TestDataFactory.server());
            entityManager.clear();

            assertThat(serverRepository.findById(saved.getServerUuid()))
                    .isPresent()
                    .get()
                    .satisfies(server -> {
                        assertThat(server.getIpAddress()).isEqualTo(TestDataFactory.loopback());
                        assertThat(server.getPort()).isEqualTo(25565);
                    });
        }

        @Test
        @DisplayName("rejects a server without a port because the column is not nullable")
        void shouldRejectServerWithoutPort() {
            ServerEntity server = TestDataFactory.server();
            server.setPort(null);

            assertThatThrownBy(() -> serverRepository.saveAndFlush(server))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("rejects a server without an IP address because the column is not nullable")
        void shouldRejectServerWithoutIpAddress() {
            ServerEntity server = TestDataFactory.server();
            server.setIpAddress(null);

            assertThatThrownBy(() -> serverRepository.saveAndFlush(server))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("countReportsByServerUuid")
    class CountReportsByServerUuid {

        @Test
        @DisplayName("counts a server's reports")
        void shouldCountReports() {
            ServerEntity server = persistServerWithReports(3);
            entityManager.clear();

            assertThat(serverRepository.countReportsByServerUuid(server.getServerUuid())).isEqualTo(3);
        }

        @Test
        @DisplayName("returns 0 for a server without reports")
        void shouldReturnZeroWithoutReports() {
            ServerEntity server = persistServerWithReports(0);
            entityManager.clear();

            // The LEFT JOIN in the query exists for exactly this - an INNER JOIN would return no row
            // here and count() would land on 0, but without this test that would go unchecked.
            assertThat(serverRepository.countReportsByServerUuid(server.getServerUuid())).isZero();
        }

        @Test
        @DisplayName("returns 0 for an unknown server UUID")
        void shouldReturnZeroForUnknownServer() {
            assertThat(serverRepository.countReportsByServerUuid(UUID.randomUUID())).isZero();
        }

        @Test
        @DisplayName("counts only the reports of the requested server")
        void shouldNotCountOtherServersReports() {
            ServerEntity first = persistServerWithReports(2);
            persistServerWithReports(5);
            entityManager.clear();

            assertThat(serverRepository.countReportsByServerUuid(first.getServerUuid())).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findAllByOrderByServerUuidAsc")
    class FindAllOrdered {

        @Test
        @DisplayName("sorts ascending by server UUID")
        void shouldSortByUuidAscending() {
            persistServerWithReports(0);
            persistServerWithReports(0);
            persistServerWithReports(0);
            entityManager.clear();

            var page = serverRepository.findAllByOrderByServerUuidAsc(PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(3);
            // Postgres compares the uuid type byte by byte, which matches the lexicographic order of
            // the canonical hex form. UUID#compareTo in Java compares the two long halves as signed
            // values and yields a different order - so this has to assert against the database's
            // order, not against Java's.
            assertThat(page.getContent())
                    .extracting(server -> server.getServerUuid().toString())
                    .isSortedAccordingTo(Comparator.naturalOrder());
        }

        @Test
        @DisplayName("respects the page size")
        void shouldPaginate() {
            persistServerWithReports(0);
            persistServerWithReports(0);
            persistServerWithReports(0);
            entityManager.clear();

            var page = serverRepository.findAllByOrderByServerUuidAsc(PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
        }
    }
}
