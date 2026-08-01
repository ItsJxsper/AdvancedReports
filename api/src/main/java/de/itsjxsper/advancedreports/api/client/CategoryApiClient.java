package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.api.model.PageResponse;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryReportCountDto;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * REST client for {@code /api/v1/categories} (see {@code CategoryController}).
 * All endpoints only use {@code @RateLimited} with default values
 * (server header is enough; no Player/Discord header required).
 * <p>
 * Note: The package name deliberately follows that of the {@code common} module’s
 * {@code de.itsjxsper.advancedreports.common.model.catogory} (typo in the
 * original package – carried over here to ensure compatibility with the existing DTOs.
 * Rename in consultation with the common team if necessary).
 */
public class CategoryApiClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/v1/categories";


    public CategoryApiClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper, Executor executor) {
        super(httpClient, baseUrl, objectMapper, executor);
    }

    public CompletableFuture<PageResponse<CategoryDto>> getCategories(int page, int size) {
        String path = BASE_PATH + "?page=" + page + "&size=" + size;
        return getAsync(path, new TypeReference<PageResponse<CategoryDto>>() {
        });
    }

    public CompletableFuture<CategoryDto> createCategory(CategoryDto dto) {
        return postAsync(BASE_PATH, dto, CategoryDto.class);
    }

    /**
     * Corresponds to {@code PATCH /api/v1/categories/} – Please note: The controller expects
     * the category ID to be updated in the body ({@code categoryDto.id()}), not in the path.
     */
    public CompletableFuture<CategoryDto> updateCategory(CategoryDto dto) {
        return patchAsync(BASE_PATH + "/", dto, CategoryDto.class);
    }

    public CompletableFuture<Void> deleteCategory(long categoryId) {
        return deleteAsync(BASE_PATH + "/" + categoryId);
    }

    public CompletableFuture<CategoryDto> getCategory(long categoryId) {
        return getAsync(BASE_PATH + "/" + categoryId, CategoryDto.class);
    }

    public CompletableFuture<CategoryDto> getCategoryWithReports(long categoryId) {
        return getAsync(BASE_PATH + "/" + categoryId + "/reports", CategoryDto.class);
    }

    public CompletableFuture<Long> countCategories() {
        return getAsync(BASE_PATH + "/count", Long.class);
    }

    public CompletableFuture<List<CategoryReportCountDto>> getReportCountPerCategory() {
        return getAsync(BASE_PATH + "/reports/count", new TypeReference<List<CategoryReportCountDto>>() {
        });
    }

    public CompletableFuture<List<CategoryDto>> getCategoriesWithActiveReports() {
        return getAsync(BASE_PATH + "/reports/active", new TypeReference<List<CategoryDto>>() {
        });
    }

}
