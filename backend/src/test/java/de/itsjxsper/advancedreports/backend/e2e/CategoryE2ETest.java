package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
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
@DisplayName("E2E: Categories")
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
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("creates a category, reads, updates and deletes it again")
        void shouldRunFullCrudCycle() {
            CategoryDto created = createCategory("cheating");

            assertThat(created).isNotNull();
            assertThat(created.id()).isNotNull();
            assertThat(created.name()).isEqualTo("cheating");

            // Read
            ResponseEntity<CategoryDto> fetched = client().get()
                    .uri("/api/v1/categories/{id}", created.id())
                    .retrieve()
                    .toEntity(CategoryDto.class);

            assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(fetched.getBody().name()).isEqualTo("cheating");
            assertThat(fetched.getBody().cooldownSec()).isEqualTo(60L);

            // Update - the mapping is "/api/v1/categories/" with a trailing slash
            CategoryDto update = new CategoryDto(created.id(), "cheating", "Cheating & Hacking",
                    "Updated description", 300L, true);

            ResponseEntity<CategoryDto> updated = client().patch()
                    .uri("/api/v1/categories/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(update)
                    .retrieve()
                    .toEntity(CategoryDto.class);

            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody().displayName()).isEqualTo("Cheating & Hacking");
            assertThat(updated.getBody().cooldownSec()).isEqualTo(300L);

            // Delete
            assertThat(client().delete()
                    .uri("/api/v1/categories/{id}", created.id())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Afterwards it can no longer be found
            assertThat(client().get()
                    .uri("/api/v1/categories/{id}", created.id())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("really persists the category in the database")
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
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("answers 409 CATEGORY_ALREADY_EXISTS for a duplicate name")
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
        @DisplayName("answers 404 CATEGORY_NOT_FOUND for an unknown id")
        void shouldReturnNotFound() {
            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/categories/9999")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.CATEGORY_NOT_FOUND);
        }

        @Test
        @DisplayName("answers 404 when deleting an unknown category")
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
    @DisplayName("Listing and aggregation")
    class Listings {

        @Test
        @DisplayName("returns every category with pagination")
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
        @DisplayName("returns the report count per category, even when it is zero")
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
        @DisplayName("returns an empty list while no category has active reports")
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
        @DisplayName("returns a category including its (still empty) reports")
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
