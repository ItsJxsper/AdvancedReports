package de.itsjxsper.advancedreports.plugin;

import de.itsjxsper.advancedreports.plugin.loader.cache.VersionCache;
import de.itsjxsper.advancedreports.plugin.loader.model.DependencySpec;
import de.itsjxsper.advancedreports.plugin.loader.resolve.LatestVersionResolver;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("UnstableApiUsage")
class AdvancedReportsPluginLoader implements PluginLoader {

    private static final List<DependencySpec> DEPENDENCY_SPECS = List.of(
            DependencySpec.githubRelease("ItsJxsper/AdvancedReports", "common-*.jar ")
    );

    private static final Path LIBS_DIR = Path.of("libs");

    private final OkHttpClient httpClient = new OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final AdvancedReportsPlugin plugin = AdvancedReportsPlugin.getInstance();

    @Override
    public void classloader(final PluginClasspathBuilder builder) {
        try {
            Files.createDirectories(LIBS_DIR);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create libs directory");
        }

        VersionCache versionCache = new VersionCache(LIBS_DIR);
        LatestVersionResolver resolver = new LatestVersionResolver();

        for (DependencySpec spec : DEPENDENCY_SPECS) {
            handleDependency(builder, spec, versionCache, resolver);
        }
    }

    private void handleDependency(PluginClasspathBuilder classpathBuilder, @NonNull DependencySpec dependencySpec, @NonNull VersionCache cache, @NonNull LatestVersionResolver resolver) {

        String installed = cache.getInstalledVersion(dependencySpec.cacheKey());
        String latest = resolver.resolveLatest(dependencySpec);

        // Remote check failed or offline -> fall back to the installed version
        if (latest == null) {
            if (installed != null) {
                loadLocalJar(classpathBuilder, dependencySpec, installed);
            } else {
                System.err.println("[SmartPluginLoader] Could not determine a version for "
                        + dependencySpec.cacheKey() + " (no network and nothing installed locally)");
            }
            return;
        }

        boolean upToDate = latest.equals(installed) && localJarExists(dependencySpec, installed);

        if (upToDate) {
            // Already up to date -> skip download, load directly from libs/
            loadLocalJar(classpathBuilder, dependencySpec, installed);
            return;
        }

        // New or outdated -> download, cache the version, then load it
        downloadAndInstall(dependencySpec, latest);
        cache.setInstalledVersion(dependencySpec.cacheKey(), latest);
        loadLocalJar(classpathBuilder, dependencySpec, latest);
    }

    private boolean localJarExists(DependencySpec spec, String version) {
        return Files.exists(localJarPath(spec, version));
    }

    private @NonNull Path localJarPath(@NonNull DependencySpec spec, String version) {
        String fileName = switch (spec.source()) {
            case MAVEN_CENTRAL -> spec.artifactId() + "-" + version + ".jar";
            case GITHUB_RELEASE -> spec.githubRepo().replace("/", "_") + "-" + version + ".jar";
        };
        return LIBS_DIR.resolve(fileName);
    }

    private void loadLocalJar(@NonNull PluginClasspathBuilder classpathBuilder, DependencySpec spec, String version) {
        classpathBuilder.addLibrary(new JarLibrary(localJarPath(spec, version)));
    }

    private void downloadAndInstall(DependencySpec spec, String version) {
        Path target = localJarPath(spec, version);
        try {
            switch (spec.source()) {
                case MAVEN_CENTRAL -> downloadMavenArtifact(spec, version, target);
                case GITHUB_RELEASE -> downloadGithubAsset(spec, version, target);
            }
            System.out.println("[SmartPluginLoader] Installed " + spec.cacheKey() + " -> " + version);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download dependency " + spec.cacheKey(), e);
        }
    }

    private void downloadMavenArtifact(@NonNull DependencySpec spec, String version, Path target) throws IOException {
        String groupPath = spec.groupId().replace('.', '/');
        String url = spec.repoUrl()
                + (spec.repoUrl().endsWith("/") ? "" : "/")
                + groupPath + "/" + spec.artifactId() + "/" + version + "/"
                + spec.artifactId() + "-" + version + ".jar";

        downloadFile(url, target);
    }

    private void downloadGithubAsset(@NonNull DependencySpec spec, String version, Path target) throws IOException {
        String apiUrl = "https://api.github.com/repos/" + spec.githubRepo() + "/releases/tags/" + version;

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build();

        String body;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("GitHub API request failed with status " + response.code());
            }
            body = response.body().string();
        }

        Pattern assetPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");
        Matcher matcher = assetPattern.matcher(body);

        String downloadUrl = null;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (matchesPattern(candidate, spec.assetNamePattern())) {
                downloadUrl = candidate;
                break;
            }
        }

        if (downloadUrl == null) {
            throw new IOException("No matching release asset found for pattern: " + spec.assetNamePattern());
        }

        downloadFile(downloadUrl, target);
    }

    private boolean matchesPattern(@NonNull String fileName, @NonNull String pattern) {
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return fileName.matches(".*" + regex + "$");
    }

    private void downloadFile(String url, Path target) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        Path tmp = Files.createTempFile("download", ".jar");

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Files.deleteIfExists(tmp);
                throw new IOException("Download failed with status " + response.code());
            }

            try (BufferedSink sink = Okio.buffer(Okio.sink(tmp))) {
                sink.writeAll(response.body().source());
            }
        }

        Files.createDirectories(target.getParent());
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
