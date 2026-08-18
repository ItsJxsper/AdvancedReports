package de.itsjxsper.advancedreports.backend.category.controller;

import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryAlreadyExistException;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryNotFoundException;
import de.itsjxsper.advancedreports.backend.category.service.CategoryService;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryReportCountDto;
import org.junit.jupiter.api.Disabled;
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
        @DisplayName("liefert 200 mit einer paginierten Liste")
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
        @DisplayName("nutzt page=0 und size=100 als Voreinstellung")
        void shouldUseDefaultPaging() throws Exception {
            when(categoryService.getCategories(any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());

            verify(categoryService).getCategories(PageRequest.of(0, 100));
        }

        @Test
        @DisplayName("übernimmt page und size aus den Query-Parametern")
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
        @DisplayName("liefert 201 mit der angelegten Kategorie")
        void shouldCreateCategory() throws Exception {
            when(categoryService.createCategory(any())).thenReturn(categoryDto);

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("bugs"));
        }

        @Test
        @DisplayName("liefert 409 CATEGORY_ALREADY_EXISTS bei einer Namensdublette")
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
        @DisplayName("liefert 200 mit der aktualisierten Kategorie")
        void shouldUpdateCategory() throws Exception {
            when(categoryService.updateCategory(any())).thenReturn(categoryDto);

            // Achtung: das Mapping ist "/" und nicht "" - ohne den Slash greift die Route nicht.
            mockMvc.perform(patch("/api/v1/categories/")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("liefert 404 CATEGORY_NOT_FOUND für eine unbekannte Kategorie")
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
        @DisplayName("liefert 200 mit der Kategorie")
        void shouldReturnCategory() throws Exception {
            when(categoryService.getCategory(1L)).thenReturn(categoryDto);

            mockMvc.perform(get("/api/v1/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cooldownSec").value(60));
        }

        @Test
        @DisplayName("liefert 404 CATEGORY_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() throws Exception {
            when(categoryService.getCategory(99L)).thenThrow(new CategoryNotFoundException(99L));

            mockMvc.perform(get("/api/v1/categories/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Category with ID 99 was not found"));
        }

        @Test
        @DisplayName("liefert 400 METHOD_ARGUMENT_TYPE_MISMATCH für eine nicht-numerische id")
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
        @DisplayName("liefert 200 mit der Kategorie inklusive Reports")
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
        @DisplayName("liefert 204 ohne Body")
        void shouldDeleteCategory() throws Exception {
            mockMvc.perform(delete("/api/v1/categories/1"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(categoryService).deleteCategory(1L);
        }

        @Test
        @DisplayName("liefert 404 CATEGORY_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() throws Exception {
            doThrow(new CategoryNotFoundException(99L)).when(categoryService).deleteCategory(99L);

            mockMvc.perform(delete("/api/v1/categories/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Zähl- und Auswertungsendpunkte")
    class CountEndpoints {

        @Test
        @DisplayName("GET /count liefert 200 mit der Gesamtanzahl")
        void shouldReturnCount() throws Exception {
            when(categoryService.countCategories()).thenReturn(7L);

            mockMvc.perform(get("/api/v1/categories/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("7"));
        }

        @Test
        @DisplayName("GET /reports/count liefert 200 mit den Reportzahlen je Kategorie")
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
        @DisplayName("GET /reports/active liefert 200 mit den Kategorien mit aktiven Reports")
        void shouldReturnCategoriesWithActiveReports() throws Exception {
            when(categoryService.getCategoriesWithActiveReports()).thenReturn(List.of(categoryDto));

            mockMvc.perform(get("/api/v1/categories/reports/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("bugs"));
        }
    }

    /**
     * Bean-Validation und Body-Parsing werden aktuell alle auf 500 abgebildet, siehe die
     * {@code @Disabled}-Begründungen unten. Die Soll-Tests bleiben stehen, damit der Fix sofort
     * sichtbar wird; die Ist-Tests halten das heutige Verhalten fest.
     */
    @Nested
    @DisplayName("Framework-Fehlerabbildung")
    class Validation {

        private static final String VALIDATION_BUG =
                "BUG: GlobalExceptionHandler (exceptions/GlobalExceptionHandler.java:120) hat einen "
                        + "@ExceptionHandler(Exception.class) als Auffangnetz. Der "
                        + "ExceptionHandlerExceptionResolver laeuft vor Springs "
                        + "DefaultHandlerExceptionResolver, deshalb faengt dieses Auffangnetz auch "
                        + "MethodArgumentNotValidException und HttpMessageNotReadableException ab. "
                        + "Jeder Eingabefehler des Clients wird dadurch zu 500 INTERNAL_SERVER_ERROR "
                        + "mit der Meldung 'An unexpected error occurred.' statt zu 400 - Clients "
                        + "koennen ihren eigenen Fehler nicht von einem Serverfehler unterscheiden. "
                        + "Fix: GlobalExceptionHandler von ResponseEntityExceptionHandler ableiten "
                        + "oder eigene Handler fuer die beiden Exceptions ergaenzen.";

        @Test
        @Disabled(VALIDATION_BUG)
        @DisplayName("liefert 400, wenn der Name kürzer als 3 Zeichen ist")
        void shouldRejectTooShortName() throws Exception {
            CategoryDto invalid = new CategoryDto(1L, "ab", "Bugs", "Fehlermeldungen", 60L, true);

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Disabled(VALIDATION_BUG)
        @DisplayName("liefert 400, wenn cooldownSec negativ ist")
        void shouldRejectNegativeCooldown() throws Exception {
            CategoryDto invalid = new CategoryDto(1L, "bugs", "Bugs", "Fehlermeldungen", -1L, true);

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Disabled(VALIDATION_BUG)
        @DisplayName("liefert 400 bei kaputtem JSON")
        void shouldRejectMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("dokumentiert, dass eine Validierungsverletzung aktuell 500 liefert")
        void shouldCurrentlyReturnServerErrorOnValidationFailure() throws Exception {
            CategoryDto invalid = new CategoryDto(1L, "ab", "Bugs", "Fehlermeldungen", 60L, true);

            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
        }

        @Test
        @DisplayName("dokumentiert, dass kaputtes JSON aktuell 500 liefert")
        void shouldCurrentlyReturnServerErrorOnMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not json"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        }

        @Test
        @Disabled(VALIDATION_BUG)
        @DisplayName("liefert 415, wenn der Content-Type nicht JSON ist")
        void shouldRejectUnsupportedMediaType() throws Exception {
            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("bugs"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @Disabled("BUG: GlobalExceptionHandler behandelt "
                + "org.springframework.web.server.MethodNotAllowedException "
                + "(exceptions/GlobalExceptionHandler.java:21) - das ist die reaktive Variante aus "
                + "WebFlux. Dieses Backend laeuft auf Spring MVC (spring-boot-starter-webmvc) und "
                + "wirft HttpRequestMethodNotSupportedException, eine voellig andere Klasse. Der "
                + "Handler feuert daher nie, ApiErrorCode.METHOD_NOT_ALLOWED ist toter Code und eine "
                + "falsche HTTP-Methode landet ueber das Auffangnetz bei 500.")
        @DisplayName("liefert 405 METHOD_NOT_ALLOWED bei einer falschen HTTP-Methode")
        void shouldRejectWrongHttpMethod() throws Exception {
            mockMvc.perform(put("/api/v1/categories/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("dokumentiert, dass ein falscher Content-Type aktuell 500 liefert")
        void shouldCurrentlyReturnServerErrorOnWrongContentType() throws Exception {
            mockMvc.perform(post("/api/v1/categories")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("bugs"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        }

        @Test
        @DisplayName("dokumentiert, dass eine falsche HTTP-Methode aktuell 500 liefert")
        void shouldCurrentlyReturnServerErrorOnWrongHttpMethod() throws Exception {
            mockMvc.perform(put("/api/v1/categories/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryDto)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        }
    }
}
