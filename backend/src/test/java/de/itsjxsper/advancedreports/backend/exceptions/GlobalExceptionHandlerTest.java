package de.itsjxsper.advancedreports.backend.exceptions;

import de.itsjxsper.advancedreports.backend.category.controller.CategoryController;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryAlreadyExistException;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryNotFoundException;
import de.itsjxsper.advancedreports.backend.category.service.CategoryService;
import de.itsjxsper.advancedreports.backend.discord.exceptions.DiscordUserNotFoundException;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerAlreadyExistException;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.MissingHeaderException;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.RateLimitExceededException;
import de.itsjxsper.advancedreports.backend.reports.exceptions.ReportNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import de.itsjxsper.advancedreports.backend.server.exceptions.ServerNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the exception-to-status mapping table in {@link GlobalExceptionHandler} in one place.
 * <p>
 * {@link CategoryController} is only the vehicle: the mocked service is made to throw each exception
 * type in turn, which is enough to drive the {@code @RestControllerAdvice}. That includes
 * {@code MissingHeaderException} and {@code RateLimitExceededException}, which in production come from
 * the rate limit aspect rather than a service — what is under test here is the mapping, not the
 * thrower.
 */
@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private static final UUID SOME_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    static Stream<Arguments> exceptionMappings() {
        return Stream.of(
                Arguments.of(new PlayerAlreadyExistException(SOME_UUID), 409, "PLAYER_ALREADY_EXISTS"),
                Arguments.of(new PlayerNotFoundException(SOME_UUID), 404, "PLAYER_NOT_FOUND"),
                Arguments.of(new DiscordUserNotFoundException(SOME_UUID), 404, "DISCORD_USER_NOT_FOUND"),
                Arguments.of(new DiscordUserNotFoundException(1L), 404, "DISCORD_USER_NOT_FOUND"),
                Arguments.of(new CategoryAlreadyExistException("bugs"), 409, "CATEGORY_ALREADY_EXISTS"),
                Arguments.of(new CategoryNotFoundException(1L), 404, "CATEGORY_NOT_FOUND"),
                Arguments.of(new ServerNotFoundException(SOME_UUID), 404, "SERVER_NOT_FOUND"),
                Arguments.of(new ScreenshotNotFoundException(1L), 404, "SCREENSHOT_NOT_FOUND"),
                Arguments.of(new ScreenshotStorageException("S3 weg"), 503, "SCREENSHOT_STORAGE_ERROR"),
                Arguments.of(new ReportNotFoundException(1L), 404, "REPORT_NOT_FOUND"),
                Arguments.of(new RateLimitExceededException("player-1", 1_000_000L), 429, "RATE_LIMIT_EXCEEDED"),
                Arguments.of(new MissingHeaderException("X-Server-UUID"), 400, "MISSING_HEADER"),
                Arguments.of(new IllegalArgumentException("kaputt"), 400, "ILLEGAL_ARGUMENT"),
                Arguments.of(new UnsupportedOperationException("not supported"), 400, "UNSUPPORTED_OPERATION"),
                Arguments.of(new IllegalStateException("unerwartet"), 500, "INTERNAL_SERVER_ERROR")
        );
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @MethodSource("exceptionMappings")
    @DisplayName("maps every domain exception to a status and an ApiErrorCode")
    void shouldMapExceptionToStatusAndCode(RuntimeException exception, int expectedStatus, String expectedCode)
            throws Exception {
        when(categoryService.getCategory(1L)).thenThrow(exception);

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    @Test
    @DisplayName("passes the original message through for a domain exception")
    void shouldPassThroughDomainMessage() throws Exception {
        when(categoryService.getCategory(1L)).thenThrow(new CategoryNotFoundException(1L));

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(jsonPath("$.message").value("Category with ID 1 was not found"));
    }

    @Test
    @DisplayName("withholds the internal message on an unexpected error")
    void shouldHideInternalMessage() throws Exception {
        when(categoryService.getCategory(1L))
                .thenThrow(new IllegalStateException("Verbindung zu Postgres verloren"));

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Postgres"))));
    }

    @Test
    @DisplayName("returns exactly the three fields status, code and message")
    void shouldReturnExactlyThreeFields() throws Exception {
        when(categoryService.getCategory(1L)).thenThrow(new CategoryNotFoundException(1L));

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(3)));
    }
}
