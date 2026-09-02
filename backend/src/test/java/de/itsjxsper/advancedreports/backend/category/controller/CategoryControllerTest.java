package de.itsjxsper.advancedreports.backend.category.controller;

import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryAlreadyExistException;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryNotFoundException;
import de.itsjxsper.advancedreports.backend.category.service.CategoryService;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryReportCountDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web slice for the category endpoints. The service is mocked, so what is under test is the HTTP
 * contract: routes, status codes, request binding and the error bodies produced by
 * {@code GlobalExceptionHandler}, which is part of the slice because it is a {@code @RestControllerAdvice}.
 * <p>
 * The rate limit aspect is deliberately not loaded here — header enforcement is covered by
 * {@code RateLimitAspectTest} and end-to-end by {@code RateLimitE2ETest}.
 */
@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
@DisplayName("CategoryController")
class CategoryControllerTest {

    private final CategoryDto categoryDto =
            new CategoryDto(1L, "bugs", "Bugs", "Fehlermeldungen", 60L, true);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private CategoryService categoryService;

    @Nested
    @DisplayName("GET /api/v1/categories")
    class GetAllCategories {

        @Test
        @DisplayName("returns 200 with a paginated list")
        void shouldReturnPagedCategories() throws Exception {
            when(categoryService.getCategories(any()))
                    .thenReturn(new PageImpl<>(List.of(categoryDto)));

            mockMvc.perform(get("/api/v1/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("bugs"))
                    .andExpect(jsonPath("$.content[0].displayName").value("Bugs"));
        }

        @Test
        @DisplayName("defaults to page=0 and size=100")
        void shouldUseDefaultPaging() throws Exception {
            when(categoryService.getCategories(any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());

            verify(categoryService).getCategories(PageRequest.of(0, 100));
        }

        @Test
        @DisplayName("takes page and size from the query parameters")
        void shouldHonourPagingParameters() throws Exception {
            when(categoryService.getCategories(any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/categories").param("page", "2").param("size", "5"))
                    .andExpect(status().isOk());

            verify(categoryService).getCategories(PageRequest.of(2, 5));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/categories")
    class CreateCategory {

        @Test
        @DisplayName("returns 201 with the created category")
        void shouldCreateCategory() throws Exception {
            when(categoryService.createCategory(any())).thenReturn(categoryDto);

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("bugs"));
        }

        @Test
        @DisplayName("returns 409 CATEGORY_ALREADY_EXISTS for a duplicate name")
        void shouldReturnConflict() throws Exception {
            when(categoryService.createCategory(any()))
                    .thenThrow(new CategoryAlreadyExistException("bugs"));

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"))
                    .andExpect(jsonPath("$.message").value("Category with name 'bugs' already exists"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/categories/")
    class UpdateCategory {

        @Test
        @DisplayName("returns 200 with the updated category")
        void shouldUpdateCategory() throws Exception {
            when(categoryService.updateCategory(any())).thenReturn(categoryDto);

            // Careful: the mapping is "/" and not "" - without the slash the route does not match.
            mockMvc.perform(patch("/api/v1/categories/")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("returns 404 CATEGORY_NOT_FOUND for an unknown category")
        void shouldReturnNotFound() throws Exception {
            when(categoryService.updateCategory(any())).thenThrow(new CategoryNotFoundException(99L));

            mockMvc.perform(patch("/api/v1/categories/")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/categories/{categoryId}")
    class GetCategory {

        @Test
        @DisplayName("returns 200 with the category")
        void shouldReturnCategory() throws Exception {
            when(categoryService.getCategory(1L)).thenReturn(categoryDto);

            mockMvc.perform(get("/api/v1/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cooldownSec").value(60));
        }

        @Test
        @DisplayName("returns 404 CATEGORY_NOT_FOUND for an unknown id")
        void shouldReturnNotFound() throws Exception {
            when(categoryService.getCategory(99L)).thenThrow(new CategoryNotFoundException(99L));

            mockMvc.perform(get("/api/v1/categories/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Category with ID 99 was not found"));
        }

        @Test
        @DisplayName("returns 400 METHOD_ARGUMENT_TYPE_MISMATCH for a non-numeric id")
        void shouldRejectNonNumericId() throws Exception {
            mockMvc.perform(get("/api/v1/categories/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("METHOD_ARGUMENT_TYPE_MISMATCH"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/categories/{categoryId}/reports")
    class GetCategoryWithReports {

        @Test
        @DisplayName("returns 200 with the category including its reports")
        void shouldReturnCategoryWithReports() throws Exception {
            when(categoryService.getCategoryWithReports(1L)).thenReturn(categoryDto);

            mockMvc.perform(get("/api/v1/categories/1/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("bugs"));

            verify(categoryService).getCategoryWithReports(1L);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/categories/{categoryId}")
    class DeleteCategory {

        @Test
        @DisplayName("returns 204 with no body")
        void shouldDeleteCategory() throws Exception {
            mockMvc.perform(delete("/api/v1/categories/1"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(categoryService).deleteCategory(1L);
        }

        @Test
        @DisplayName("returns 404 CATEGORY_NOT_FOUND for an unknown id")
        void shouldReturnNotFound() throws Exception {
            doThrow(new CategoryNotFoundException(99L)).when(categoryService).deleteCategory(99L);

            mockMvc.perform(delete("/api/v1/categories/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Count and aggregation endpoints")
    class CountEndpoints {

        @Test
        @DisplayName("GET /count returns 200 with the total count")
        void shouldReturnCount() throws Exception {
            when(categoryService.countCategories()).thenReturn(7L);

            mockMvc.perform(get("/api/v1/categories/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("7"));
        }

        @Test
        @DisplayName("GET /reports/count returns 200 with the report counts per category")
        void shouldReturnReportCountsPerCategory() throws Exception {
            when(categoryService.countCategoriesByReportCount()).thenReturn(List.of(
                    new CategoryReportCountDto(1L, "bugs", 3L),
                    new CategoryReportCountDto(2L, "cheating", 0L)));

            mockMvc.perform(get("/api/v1/categories/reports/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("bugs"))
                    .andExpect(jsonPath("$[0].reportCount").value(3))
                    .andExpect(jsonPath("$[1].reportCount").value(0));
        }

        @Test
        @DisplayName("GET /reports/active returns 200 with the categories that have active reports")
        void shouldReturnCategoriesWithActiveReports() throws Exception {
            when(categoryService.getCategoriesWithActiveReports()).thenReturn(List.of(categoryDto));

            mockMvc.perform(get("/api/v1/categories/reports/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("bugs"));
        }
    }

    /**
     * Bean validation and body parsing run through ResponseEntityExceptionHandler, which
     * GlobalExceptionHandler extends. Without that inheritance the
     * {@code @ExceptionHandler(Exception.class)} safety net catches the framework exceptions too
     * and turns every client error into a 500.
     */
    @Nested
    @DisplayName("Framework error mapping")
    class Validation {

        @Test
        @DisplayName("returns 400 when the name is shorter than 3 characters")
        void shouldRejectTooShortName() throws Exception {
            CategoryDto invalid = new CategoryDto(1L, "ab", "Bugs", "Fehlermeldungen", 60L, true);

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when cooldownSec is negative")
        void shouldRejectNegativeCooldown() throws Exception {
            CategoryDto invalid = new CategoryDto(1L, "bugs", "Bugs", "Fehlermeldungen", -1L, true);

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 for malformed JSON")
        void shouldRejectMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 415 when the content type is not JSON")
        void shouldRejectUnsupportedMediaType() throws Exception {
            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("bugs"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("returns 405 METHOD_NOT_ALLOWED for a wrong HTTP method")
        void shouldRejectWrongHttpMethod() throws Exception {
            mockMvc.perform(put("/api/v1/categories/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }

    }
}
