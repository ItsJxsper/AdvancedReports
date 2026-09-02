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
    @DisplayName("Speichern")
    class Persisting {

        @Test
        @DisplayName("übernimmt die mitgegebene Server-UUID unverändert")
        void shouldKeepAssignedServerUuid() {
            UUID serverUuid = UUID.randomUUID();

            ServerEntity saved = entityManager.persistAndFlush(TestDataFactory.server(serverUuid));

            // ServerEntity hat bewusst kein @GeneratedValue: der Minecraft-Server registriert sich
            // unter seiner eigenen, konfigurierten UUID, die ServerDto#serverUUID auch @NotNull
            // mitschickt. Ein Generator würde genau diese UUID beim Persistieren überschreiben.
            assertThat(saved.getServerUuid()).isEqualTo(serverUuid);
        }

        @Test
        @DisplayName("lehnt einen Server ohne UUID ab, weil der Identifier zugewiesen werden muss")
        void shouldRejectServerWithoutUuid() {
            ServerEntity server = TestDataFactory.server();
            server.setServerUuid(null);

            assertThatThrownBy(() -> entityManager.persistAndFlush(server))
                    .isInstanceOf(IdentifierGenerationException.class);
        }

        @Test
        @DisplayName("speichert die IP-Adresse und den Port")
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
        @DisplayName("lehnt einen Server ohne Port ab, weil die Spalte nicht nullable ist")
        void shouldRejectServerWithoutPort() {
            ServerEntity server = TestDataFactory.server();
            server.setPort(null);

            assertThatThrownBy(() -> serverRepository.saveAndFlush(server))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("lehnt einen Server ohne IP-Adresse ab, weil die Spalte nicht nullable ist")
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
        @DisplayName("zählt die Reports eines Servers")
        void shouldCountReports() {
            ServerEntity server = persistServerWithReports(3);
            entityManager.clear();

            assertThat(serverRepository.countReportsByServerUuid(server.getServerUuid())).isEqualTo(3);
        }

        @Test
        @DisplayName("liefert 0 für einen Server ohne Reports")
        void shouldReturnZeroWithoutReports() {
            ServerEntity server = persistServerWithReports(0);
            entityManager.clear();

            // Der LEFT JOIN in der Query ist genau dafür da - ein INNER JOIN würde hier keine Zeile
            // liefern und count() käme auf 0, aber ohne diesen Test bliebe das ungeprüft.
            assertThat(serverRepository.countReportsByServerUuid(server.getServerUuid())).isZero();
        }

        @Test
        @DisplayName("liefert 0 für eine unbekannte Server-UUID")
        void shouldReturnZeroForUnknownServer() {
            assertThat(serverRepository.countReportsByServerUuid(UUID.randomUUID())).isZero();
        }

        @Test
        @DisplayName("zählt nur die Reports des angefragten Servers")
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
        @DisplayName("sortiert aufsteigend nach Server-UUID")
        void shouldSortByUuidAscending() {
            persistServerWithReports(0);
            persistServerWithReports(0);
            persistServerWithReports(0);
            entityManager.clear();

            var page = serverRepository.findAllByOrderByServerUuidAsc(PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(3);
            // Postgres vergleicht den uuid-Typ byteweise, was der lexikografischen Ordnung der
            // kanonischen Hex-Schreibweise entspricht. UUID#compareTo in Java vergleicht dagegen die
            // beiden long-Hälften vorzeichenbehaftet und liefert eine andere Reihenfolge - hier muss
            // also gegen die Ordnung der Datenbank geprüft werden, nicht gegen die von Java.
            assertThat(page.getContent())
                    .extracting(server -> server.getServerUuid().toString())
                    .isSortedAccordingTo(Comparator.naturalOrder());
        }

        @Test
        @DisplayName("respektiert die Seitengröße")
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
