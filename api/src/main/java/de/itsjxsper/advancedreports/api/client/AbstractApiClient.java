package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.itsjxsper.advancedreports.common.exceptions.ApiException;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Base class for all domain-specific API clients (Reports, Screenshots, Players, ...).
 * <p>
 * Encapsulates generic HTTP communication (GET/POST/PATCH/DELETE), JSON (de-)serialization
 * via Jackson, and uniform error handling. Dynamic headers (e.g., {@code X-Player-UUID}
 * or {@code X-Discord-ID}) are explicitly passed as a {@link Map} per call – intentionally
 * not using ThreadLocal to avoid issues with CompletableFuture/async thread pools.
 * <p>
 * All requests run on the provided {@link Executor}, NOT on
 * {@code ForkJoinPool.commonPool()}, so that blocking OkHttp calls do not flood the global
 * pool (e.g., Bukkit servers with many parallel player requests).
 */
public abstract class AbstractApiClient {

    protected static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final RequestBody EMPTY_BODY = RequestBody.create(null, new byte[0]);

    protected final OkHttpClient httpClient;
    protected final String baseUrl;
    protected final ObjectMapper objectMapper;
    protected final Executor executor;

    protected AbstractApiClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper, Executor executor) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    // ---------------------------------------------------------------------
    // GET
    // ---------------------------------------------------------------------

    protected <R> CompletableFuture<R> getAsync(String path, Class<R> responseType) {
        return getAsync(path, responseType, Map.of());
    }

    protected <R> CompletableFuture<R> getAsync(String path, Class<R> responseType, Map<String, String> headers) {
        Request request = requestBuilder(path, headers).get().build();
        return executeAsync(request, responseType);
    }

    /**
     * Variant for generic response types (e.g., {@code PageResponse<ReportDto>}),
     * where Jackson requires a {@link TypeReference} instead of a simple
     * {@code Class<R>} due to type erasure.
     */
    protected <R> CompletableFuture<R> getAsync(String path, TypeReference<R> responseType) {
        return getAsync(path, responseType, Map.of());
    }

    protected <R> CompletableFuture<R> getAsync(String path, TypeReference<R> responseType, Map<String, String> headers) {
        Request request = requestBuilder(path, headers).get().build();
        return executeAsync(request, responseType);
    }

    // ---------------------------------------------------------------------
    // POST
    // ---------------------------------------------------------------------

    protected <B, R> CompletableFuture<R> postAsync(String path, B body, Class<R> responseType) {
        return postAsync(path, body, responseType, Map.of());
    }

    protected <B, R> CompletableFuture<R> postAsync(String path, B body, Class<R> responseType, Map<String, String> headers) {
        RequestBody requestBody = toJsonBody(body);
        Request request = requestBuilder(path, headers).post(requestBody).build();
        return executeAsync(request, responseType);
    }

    /**
     * POST without body (e.g., trigger endpoints).
     */
    protected <R> CompletableFuture<R> postAsync(String path, Class<R> responseType, Map<String, String> headers) {
        Request request = requestBuilder(path, headers).post(EMPTY_BODY).build();
        return executeAsync(request, responseType);
    }

    // ---------------------------------------------------------------------
    // PATCH
    // ---------------------------------------------------------------------

    protected <B, R> CompletableFuture<R> patchAsync(String path, B body, Class<R> responseType) {
        return patchAsync(path, body, responseType, Map.of());
    }

    protected <B, R> CompletableFuture<R> patchAsync(String path, B body, Class<R> responseType, Map<String, String> headers) {
        RequestBody requestBody = toJsonBody(body);
        Request request = requestBuilder(path, headers).patch(requestBody).build();
        return executeAsync(request, responseType);
    }

    // ---------------------------------------------------------------------
    // PUT
    // ---------------------------------------------------------------------

    protected <B, R> CompletableFuture<R> putAsync(String path, B body, Class<R> responseType) {
        return putAsync(path, body, responseType, Map.of());
    }

    protected <B, R> CompletableFuture<R> putAsync(String path, B body, Class<R> responseType, Map<String, String> headers) {
        RequestBody requestBody = toJsonBody(body);
        Request request = requestBuilder(path, headers).put(requestBody).build();
        return executeAsync(request, responseType);
    }

    // ---------------------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------------------

    protected CompletableFuture<Void> deleteAsync(String path) {
        return deleteAsync(path, Map.of());
    }

    protected CompletableFuture<Void> deleteAsync(String path, Map<String, String> headers) {
        Request request = requestBuilder(path, headers).delete().build();
        return executeAsyncNoContent(request);
    }

    // ---------------------------------------------------------------------
    // Multipart Upload
    // ---------------------------------------------------------------------

    protected <R> CompletableFuture<R> uploadAsync(String path, String partName, String filename,
                                                   MediaType mediaType, byte[] content,
                                                   Class<R> responseType, Map<String, String> headers) {
        RequestBody fileBody = RequestBody.create(mediaType, content);
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(partName, filename, fileBody)
                .build();

        Request request = requestBuilder(path, headers).post(multipartBody).build();
        return executeAsync(request, responseType);
    }

    // ---------------------------------------------------------------------
    // Raw Download (z. B. Screenshot-Bytes)
    // ---------------------------------------------------------------------

    protected CompletableFuture<byte[]> downloadAsync(String path, Map<String, String> headers) {
        Request request = requestBuilder(path, headers).get().build();

        return CompletableFuture.supplyAsync(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw toApiException(response);
                }
                ResponseBody body = response.body();
                return body != null ? body.bytes() : new byte[0];
            } catch (IOException e) {
                throw new ApiException("Network error during request to " + path, e);
            }
        }, executor);
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private Request.Builder requestBuilder(String path, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(baseUrl + path);
        headers.forEach(builder::header);
        return builder;
    }

    private <B> RequestBody toJsonBody(B body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            return RequestBody.create(JSON, json);
        } catch (IOException e) {
            throw new ApiException("Could not serialize request body", e);
        }
    }

    private <R> CompletableFuture<R> executeAsync(Request request, Class<R> responseType) {
        return CompletableFuture.supplyAsync(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw toApiException(response);
                }

                if (responseType == Void.class) {
                    return null;
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    return null;
                }

                return objectMapper.readValue(responseBody.string(), responseType);
            } catch (IOException e) {
                throw new ApiException("Network error during request to " + request.url(), e);
            }
        }, executor);
    }

    private <R> CompletableFuture<R> executeAsync(Request request, TypeReference<R> responseType) {
        return CompletableFuture.supplyAsync(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw toApiException(response);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    return null;
                }

                return objectMapper.readValue(responseBody.string(), responseType);
            } catch (IOException e) {
                throw new ApiException("Network error during request to " + request.url(), e);
            }
        }, executor);
    }

    private CompletableFuture<Void> executeAsyncNoContent(Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw toApiException(response);
                }
                return null;
            } catch (IOException e) {
                throw new ApiException("Network error during request to " + request.url(), e);
            }
        }, executor);
    }

    private ApiException toApiException(Response response) {
        String bodyContent = null;
        try {
            ResponseBody body = response.body();
            bodyContent = body != null ? body.string() : null;
        } catch (IOException ignored) {
            // Body could not be read, status code is enough info
        }

        return ApiException.fromHttpResponse(response.code(), bodyContent, objectMapper);
    }
}
