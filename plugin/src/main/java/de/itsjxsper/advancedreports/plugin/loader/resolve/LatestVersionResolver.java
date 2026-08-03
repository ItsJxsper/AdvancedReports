package de.itsjxsper.advancedreports.plugin.loader.resolve;

import de.itsjxsper.advancedreports.plugin.AdvancedReportsPlugin;
import de.itsjxsper.advancedreports.plugin.loader.model.DependencySpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the latest available version of a dependency,
 * either via Maven repository metadata or the GitHub Releases API.
 */
public class LatestVersionResolver {

    private static final Pattern RELEASE_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private final OkHttpClient httpClient = new OkHttpClient().newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();
    private final AdvancedReportsPlugin plugin = AdvancedReportsPlugin.getInstance();

    /**
     * Returns the latest version, or null if the check fails (e.g., no internet connection) —
     * in that case the loader falls back to the locally installed version.
     */
    public String resolveLatest(@NonNull DependencySpec dependencySpec) {
        try {
            return switch (dependencySpec.source()) {
                case MAVEN_CENTRAL -> resolveLatestMaven(dependencySpec);
                case GITHUB_RELEASE -> resolveLatestGitHub(dependencySpec);
            };
        } catch (IOException e) {
            this.plugin.getLogger().warning("Failed to check for latest version: " + e.getMessage());
            return null; // no abort, loader falls back to the locally installed version
        }
    }

    @Contract(pure = true)
    private @Nullable String resolveLatestMaven(@NonNull DependencySpec dependencySpec) throws IOException {
        String groupPath = dependencySpec.groupId().replace('.', '/');
        String metadataUrl = dependencySpec.repoUrl()
                + (dependencySpec.repoUrl().endsWith("/") ? "" : "/")
                + groupPath + "/" + dependencySpec.artifactId() + "/maven-metadata.xml";

        Request request = new Request.Builder()
                .url(metadataUrl)
                .get()
                .build();

        try (Response response = this.httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            String body = response.body().string();
            String release = extractTag(body, "release");
            return release != null ? release : extractTag(body, "latest");
        }
    }

    private @Nullable String extractTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    @Contract(pure = true)
    private @Nullable String resolveLatestGitHub(DependencySpec dependencySpec) throws IOException {
        return null;
    }

    private @Nullable String resolveLatestGithub(@NonNull DependencySpec spec) throws IOException {
        String url = "https://api.github.com/repos/" + spec.githubRepo() + "/releases/latest";

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            Matcher m = RELEASE_PATTERN.matcher(response.body().string());
            return m.find() ? m.group(1) : null;
        }
    }
}
