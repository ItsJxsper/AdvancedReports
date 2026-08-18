package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.exceptions.ApiErrorCode;
import de.itsjxsper.advancedreports.backend.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the category endpoints over real HTTP against a running server, a real Postgres and a real
 * Redis-backed rate limiter.
 */
@DisplayName("E2E: Kategorien")
class CategoryE2ETest extends AbstractE2ETest {

    private CategoryDto createCategory(String name) {
        ResponseEntity<CategoryDto> response = client().post()
                .uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TestDataFactory.categoryDto(name))
                .retrieve()
                .toEntity(CategoryDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    @Nested
    @DisplayName("Lebenszyklus")
    class Lifecycle {

        @Test
        @DisplayName("legt eine Kategorie an, liest, ändert und löscht sie wieder")
        void shouldRunFullCrudCycle() {
            CategoryDto created = createCategory("cheating");

            assertThat(created).isNotNull();
            assertThat(created.id()).isNotNull();
            assertThat(created.name()).isEqualTo("cheating");

            // Lesen
            ResponseEntity<CategoryDto> fetched = client().get()
                    .uri("/api/v1/categories/{id}", created.id())
                    .retrieve()
                    .toEntity(CategoryDto.class);

            assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(fetched.getBody().name()).isEqualTo("cheating");
            assertThat(fetched.getBody().cooldownSec()).isEqualTo(60L);

            // Ändern - das Mapping ist "/api/v1/categories/" mit Slash am Ende
            CategoryDto update = new CategoryDto(created.id(), "cheating", "Cheating & Hacking",
                    "Aktualisierte Beschreibung", 300L, true);

            ResponseEntity<CategoryDto> updated = client().patch()
                    .uri("/api/v1/categories/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(update)
                    .retrieve()
                    .toEntity(CategoryDto.class);

            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody().displayName()).isEqualTo("Cheating & Hacking");
            assertThat(updated.getBody().cooldownSec()).isEqualTo(300L);

            // Löschen
            assertThat(client().delete()
                    .uri("/api/v1/categories/{id}", created.id())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Danach nicht mehr auffindbar
            assertThat(client().get()
                    .uri("/api/v1/categories/{id}", created.id())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("persistiert die Kategorie wirklich in der Datenbank")
        void shouldPersistAcrossRequests() {
            createCategory("cheating");

            ResponseEntity<Long> count = client().get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(count.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(count.getBody()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Fehlerfälle")
    class ErrorCases {

        @Test
        @DisplayName("antwortet mit 409 CATEGORY_ALREADY_EXISTS auf eine Namensdublette")
        void shouldRejectDuplicateName() {
            createCategory("cheating");

            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TestDataFactory.categoryDto("cheating"))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.CATEGORY_ALREADY_EXISTS);
            assertThat(response.getBody().status()).isEqualTo(409);
        }

        @Test
        @DisplayName("antwortet mit 404 CATEGORY_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() {
            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/categories/9999")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.CATEGORY_NOT_FOUND);
        }

        @Test
        @DisplayName("antwortet mit 404 beim Löschen einer unbekannten Kategorie")
        void shouldReturnNotFoundOnDelete() {
            ResponseEntity<ApiErrorResponse> response = client().delete()
                    .uri("/api/v1/categories/9999")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Listen und Auswertungen")
    class Listings {

        @Test
        @DisplayName("liefert alle Kategorien paginiert")
        void shouldListCategoriesPaged() {
            createCategory("cheating");
            createCategory("griefing");
            createCategory("chat");

            ResponseEntity<Map<String, Object>> response = client().get()
                    .uri("/api/v1/categories?page=0&size=2")
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat((List<?>) response.getBody().get("content")).hasSize(2);
            assertThat(((Number) response.getBody().get("totalElements")).intValue()).isEqualTo(3);
        }

        @Test
        @DisplayName("liefert die Reportanzahl je Kategorie, auch wenn sie null ist")
        void shouldReturnReportCountsPerCategory() {
            createCategory("cheating");
            createCategory("griefing");

            ResponseEntity<List<Map<String, Object>>> response = client().get()
                    .uri("/api/v1/categories/reports/count")
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody())
                    .allSatisfy(row -> assertThat(((Number) row.get("reportCount")).intValue()).isZero());
        }

        @Test
        @DisplayName("liefert eine leere Liste, solange keine Kategorie aktive Reports hat")
        void shouldReturnNoCategoriesWithActiveReports() {
            createCategory("cheating");

            ResponseEntity<List<CategoryDto>> response = client().get()
                    .uri("/api/v1/categories/reports/active")
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<CategoryDto>>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("liefert eine Kategorie inklusive ihrer (noch leeren) Reports")
        void shouldReturnCategoryWithReports() {
            CategoryDto created = createCategory("cheating");

            ResponseEntity<CategoryDto> response = client().get()
                    .uri("/api/v1/categories/{id}/reports", created.id())
                    .retrieve()
                    .toEntity(CategoryDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().id()).isEqualTo(created.id());
        }
    }
}
