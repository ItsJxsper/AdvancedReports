package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.exceptions.ApiErrorCode;
import de.itsjxsper.advancedreports.backend.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.DbFixtures;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the server endpoints.
 * <p>
 * Registration via REST is broken in two independent ways (see {@code Registration} below), so the
 * read and delete tests seed their server through SQL. That keeps those paths under test instead of
 * hiding them behind the registration defect.
 */
@DisplayName("E2E: Server")
class ServerE2ETest extends AbstractE2ETest {

    private static final String REQUEST_BODY_BUG =
            "BUG: ServerController#createServer (server/controller/ServerController.java:24) deklariert "
                    + "'ServerDto serverDto' ohne @RequestBody. Spring MVC bindet den Parameter daher "
                    + "als Model-Attribute und verwirft den JSON-Body komplett; der Service bekommt ein "
                    + "DTO mit lauter null-Feldern und laeuft in die NOT-NULL-Spalten von server_entity. "
                    + "Der Umweg ueber Query-Parameter scheitert an einem zweiten Problem: fuer "
                    + "ServerDto#ipAddress (java.net.InetAddress) ist kein String-Converter registriert, "
                    + "Spring meldet 'no matching editors or conversion strategy found'. Damit ist POST "
                    + "/api/v1/servers auf keinem Weg benutzbar. Dasselbe @RequestBody fehlt an "
                    + "#updateServer (Zeile 66), dort fehlt zusaetzlich das @RateLimited an #createServer. "
                    + "Fix: @RequestBody ergaenzen - Jackson deserialisiert InetAddress von Haus aus.";

    @Nested
    @DisplayName("Registrierung")
    class Registration {

        @Test
        @Disabled(REQUEST_BODY_BUG)
        @DisplayName("registriert einen Server über einen JSON-Body")
        void shouldCreateServerFromJsonBody() {
            UUID serverUuid = UUID.randomUUID();

            ResponseEntity<ServerDto> response = client().post()
                    .uri("/api/v1/servers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TestDataFactory.serverDto(serverUuid))
                    .retrieve()
                    .toEntity(ServerDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().serverUUID()).isEqualTo(serverUuid);
            assertThat(response.getBody().port()).isEqualTo(25565);
        }

        @Test
        @DisplayName("dokumentiert, dass ein JSON-Body verworfen wird und die Anfrage scheitert")
        void shouldCurrentlyFailWithJsonBody() {
            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/servers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TestDataFactory.serverDto(UUID.randomUUID()))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            // Der Body wird ignoriert, das leere DTO verletzt die NOT-NULL-Spalten.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("dokumentiert, dass auch Query-Parameter an der IP-Konvertierung scheitern")
        void shouldCurrentlyFailWithQueryParameters() {
            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/servers?serverUUID={uuid}&ipAddress=127.0.0.1&port=25565",
                            UUID.randomUUID())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            // "Failed to convert value of type 'java.lang.String' to required type
            // 'java.net.InetAddress'" - und weil der GlobalExceptionHandler ein Auffangnetz fuer
            // Exception hat, kommt daraus 500 statt 400.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("Lesen")
    class Reading {

        @Test
        @Disabled("""
                BUG: ServerMapper bildet serverUuid nicht auf serverUUID ab. Die Entity heisst \
                ServerEntity#serverUuid, das DTO ServerDto#serverUUID - MapStruct findet die \
                Zuordnung wegen des zweiten Grossbuchstabens nicht, und \
                unmappedTargetPolicy = ReportingPolicy.IGNORE unterdrueckt die Warnung. Der \
                generierte ServerMapperImpl#toDto enthaelt daher wortwoertlich
                
                  UUID serverUUID = null;
                  ServerDto serverDto = new ServerDto( serverUUID, ipAddress, port );
                
                Jede Server-Antwort liefert also serverUUID: null. Das trifft den Kern des \
                Mehrserver-Designs: Clients erfahren die UUID ihres Servers nie und koennen damit \
                weder den X-Server-UUID-Header setzen noch einen Report einem Server zuordnen. \
                Fix: @Mapping(source = "serverUuid", target = "serverUUID") ergaenzen (und die \
                Gegenrichtung ebenso) oder unmappedTargetPolicy auf ERROR stellen, damit so etwas \
                beim Bauen auffaellt. Siehe server/mapper/ServerMapper.java.""")
        @DisplayName("liefert einen Server über seine UUID")
        void shouldReturnServerByUuid() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            ResponseEntity<ServerDto> response = client().get()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toEntity(ServerDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().serverUUID()).isEqualTo(serverUuid);
            assertThat(response.getBody().port()).isEqualTo(25565);
            assertThat(response.getBody().ipAddress()).isEqualTo(TestDataFactory.loopback());
        }

        @Test
        @DisplayName("dokumentiert, dass serverUUID in der Antwort immer null ist")
        void shouldCurrentlyReturnNullServerUuid() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            ResponseEntity<ServerDto> response = client().get()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toEntity(ServerDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            // IP und Port kommen an, nur die UUID fällt beim Mapping heraus.
            assertThat(response.getBody().ipAddress()).isEqualTo(TestDataFactory.loopback());
            assertThat(response.getBody().port()).isEqualTo(25565);
            assertThat(response.getBody().serverUUID()).isNull();
        }

        @Test
        @DisplayName("antwortet mit 404 SERVER_NOT_FOUND für eine unbekannte UUID")
        void shouldReturnNotFound() {
            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/servers/{uuid}", UUID.randomUUID())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SERVER_NOT_FOUND);
        }

        @Test
        @DisplayName("zählt die registrierten Server")
        void shouldCountServers() {
            DbFixtures.insertServer(dataSource);
            DbFixtures.insertServer(dataSource);

            ResponseEntity<Long> response = client().get()
                    .uri("/api/v1/servers/count")
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(2L);
        }

        @Test
        @DisplayName("liefert eine paginierte Serverliste")
        void shouldListServersPaged() {
            DbFixtures.insertServer(dataSource);
            DbFixtures.insertServer(dataSource);
            DbFixtures.insertServer(dataSource);

            ResponseEntity<String> response = client().get()
                    .uri("/api/v1/servers?page=0&size=2")
                    .retrieve()
                    .toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"totalElements\":3");
        }

        @Test
        @DisplayName("liefert 0 Reports für einen Server ohne Reports")
        void shouldReturnZeroReportsForNewServer() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            ResponseEntity<Long> response = client().get()
                    .uri("/api/v1/servers/{uuid}/reports/count", serverUuid)
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isZero();
        }
    }

    @Nested
    @DisplayName("Löschen")
    class Deleting {

        @Test
        @Disabled("""
                BUG: DELETE /api/v1/servers/{uuid} meldet 204, loescht aber nichts. ServerService ist \
                auf Klassenebene mit @Transactional(readOnly = true) annotiert, und #deleteServer \
                (server/service/ServerService.java:69) hat - anders als #createServer, #updateServer \
                und #getAllServers - kein eigenes @Transactional. Die Methode laeuft daher in einer \
                read-only-Transaktion; Hibernate setzt darin FlushMode.MANUAL, sodass das DELETE nie \
                geflusht wird und stillschweigend verpufft. Nachweis: nach dem 204 liefert ein GET auf \
                dieselbe UUID weiterhin 200. Fix: @Transactional an #deleteServer ergaenzen (und die \
                fehlende Existenzpruefung gleich mit, siehe \
                shouldReturnNotFoundOnDeletingUnknownServer).""")
        @DisplayName("löscht einen registrierten Server")
        void shouldDeleteServer() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            assertThat(client().delete()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(client().get()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("dokumentiert, dass ein gelöschter Server weiterhin abrufbar bleibt")
        void shouldCurrentlyNotActuallyDeleteServer() {
            UUID serverUuid = DbFixtures.insertServer(dataSource);

            assertThat(client().delete()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Das DELETE wird in der read-only-Transaktion nie geflusht.
            assertThat(client().get()
                    .uri("/api/v1/servers/{uuid}", serverUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(client().get()
                    .uri("/api/v1/servers/count")
                    .retrieve()
                    .toEntity(Long.class)
                    .getBody()).isEqualTo(1L);
        }

        @Test
        @Disabled("BUG: ServerService#deleteServer (server/service/ServerService.java:71) ruft direkt "
                + "deleteById ohne Existenzpruefung. Ein DELETE auf eine unbekannte Server-UUID meldet "
                + "daher 204 No Content statt 404 SERVER_NOT_FOUND, obwohl Category, Player, Report "
                + "und DiscordPlayer vorher nachschlagen und werfen.")
        @DisplayName("antwortet mit 404 beim Löschen einer unbekannten UUID")
        void shouldReturnNotFoundOnDeletingUnknownServer() {
            ResponseEntity<ApiErrorResponse> response = client().delete()
                    .uri("/api/v1/servers/{uuid}", UUID.randomUUID())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SERVER_NOT_FOUND);
        }

        @Test
        @DisplayName("dokumentiert, dass das Löschen einer unbekannten UUID aktuell 204 liefert")
        void shouldCurrentlyReturnNoContentForUnknownServer() {
            assertThat(client().delete()
                    .uri("/api/v1/servers/{uuid}", UUID.randomUUID())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
