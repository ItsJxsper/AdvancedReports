package de.itsjxsper.advancedreports.plugin.loader.resolve;

import de.itsjxsper.advancedreports.plugin.loader.model.DependencySpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the latest available version of a dependency,
 * either via Maven repository metadata or the GitHub Releases API.
 */
public class LatestVersionResolver {

    private static final Pattern RELEASE_TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public String resolveLatest(DependencySpec dependencySpec) {
        try {
            return switch (dependencySpec.source()) {
                case MAVEN_CENTRAL -> resolveLatestMaven(dependencySpec);
                case GITHUB_RELEASE -> resolveLatestGithub(dependencySpec);
            };
        } catch (IOException e) {
            // Log the actual cause instead of silently swallowing it
            System.err.println("[LatestVersionResolver] Version check failed for "
                    + dependencySpec.cacheKey() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    private String resolveLatestMaven(DependencySpec dependencySpec) throws IOException {
        String groupPath = dependencySpec.groupId().replace('.', '/');
        String metadataUrl = dependencySpec.repoUrl()
                + (dependencySpec.repoUrl().endsWith("/") ? "" : "/")
                + groupPath + "/" + dependencySpec.artifactId() + "/maven-metadata.xml";

        Request request = new Request.Builder()
                .url(metadataUrl)
                .get()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                System.err.println("[LatestVersionResolver] Maven metadata request returned "
                        + response.code() + " for " + metadataUrl);
                return null;
            }
            String body = response.body().string();
            String release = extractTag(body, "release");
            return release != null ? release : extractTag(body, "latest");
        }
    }

    private String extractTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private String resolveLatestGithub(DependencySpec dependencySpec) throws IOException {
        String url = "https://api.github.com/repos/" + dependencySpec.githubRepo() + "/releases/latest";

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SmartPluginLoader") // GitHub API requires a User-Agent header
                .get()
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String rateLimitRemaining = response.header("X-RateLimit-Remaining");
                System.err.println("[LatestVersionResolver] GitHub API request returned "
                        + response.code() + " for " + dependencySpec.githubRepo()
                        + (rateLimitRemaining != null ? " (rate limit remaining: " + rateLimitRemaining + ")" : ""));
                return null;
            }
            Matcher m = RELEASE_TAG.matcher(response.body().string());
            return m.find() ? m.group(1) : null;
        }
    }
}