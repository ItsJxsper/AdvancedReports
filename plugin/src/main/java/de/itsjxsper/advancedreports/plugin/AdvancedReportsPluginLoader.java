package de.itsjxsper.advancedreports.plugin;

import de.itsjxsper.advancedreports.plugin.loader.cache.VersionCache;
import de.itsjxsper.advancedreports.plugin.loader.model.DependencySpec;
import de.itsjxsper.advancedreports.plugin.loader.model.ReleaseArtifact;
import de.itsjxsper.advancedreports.plugin.loader.resolve.GithubReleaseIndex;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Puts the shared {@code common} and {@code api} artifacts on the plugin classpath, downloading them
 * from the GitHub Releases of the project repository on first use and keeping them in {@link #LIBS_DIR}.
 *
 * <p>The artifacts are deliberately not shaded into the plugin jar, so a server picks up a new
 * {@code common} or {@code api} release without a plugin update.
 */
@Slf4j
@SuppressWarnings("UnstableApiUsage")
public class AdvancedReportsPluginLoader implements PluginLoader {

    private static final String GITHUB_REPO = "ItsJxsper/AdvancedReports";

    private static final List<DependencySpec> DEPENDENCY_SPECS = List.of(
            new DependencySpec(GITHUB_REPO, "common"),
            new DependencySpec(GITHUB_REPO, "api")
    );

    private static final Path LIBS_DIR = Path.of("libs");

    private final OkHttpClient httpClient = new OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public void classloader(final @NonNull PluginClasspathBuilder builder) {
        try {
            Files.createDirectories(LIBS_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create the library directory " + LIBS_DIR.toAbsolutePath(), e);
        }

        VersionCache versionCache = new VersionCache(LIBS_DIR);
        GithubReleaseIndex releaseIndex = new GithubReleaseIndex(httpClient, GITHUB_REPO);

        for (DependencySpec spec : DEPENDENCY_SPECS) {
            handleDependency(builder, spec, versionCache, releaseIndex);
        }
    }

    private void handleDependency(PluginClasspathBuilder classpathBuilder, @NonNull DependencySpec spec,
                                  @NonNull VersionCache cache, @NonNull GithubReleaseIndex releaseIndex) {

        Optional<ReleaseArtifact> latest = releaseIndex.latestFor(spec);
        String installed = cache.getInstalledVersion(spec.cacheKey());

        // Remote check failed or offline -> fall back to the installed copy, if there is a usable one
        if (latest.isEmpty()) {
            if (installed != null && localJarExists(spec, installed)) {
                log.info("Using the locally installed {} {} (remote check unavailable)", spec.cacheKey(), installed);
                loadLocalJar(classpathBuilder, spec, installed);
                return;
            }

            // Loading with an incomplete classpath only defers the failure to a confusing
            // NoClassDefFoundError later on, so refuse to load the plugin at all.
            throw new IllegalStateException("Could not resolve " + spec.cacheKey()
                    + " and no local copy exists in " + LIBS_DIR.toAbsolutePath());
        }

        String version = latest.get().version();

        // New, outdated, or the local file went missing -> download it before loading
        if (!version.equals(installed) || !localJarExists(spec, version)) {
            downloadFile(latest.get().downloadUrl(), localJarPath(spec, version), spec);
            cache.setInstalledVersion(spec.cacheKey(), version);
            log.info("Installed {} -> {}", spec.cacheKey(), version);
        }

        loadLocalJar(classpathBuilder, spec, version);
    }

    private boolean localJarExists(DependencySpec spec, String version) {
        return Files.exists(localJarPath(spec, version));
    }

    private @NonNull Path localJarPath(@NonNull DependencySpec spec, String version) {
        return LIBS_DIR.resolve(spec.jarFileName(version));
    }

    private void loadLocalJar(@NonNull PluginClasspathBuilder classpathBuilder, DependencySpec spec, String version) {
        classpathBuilder.addLibrary(new JarLibrary(localJarPath(spec, version)));
    }

    private void downloadFile(String url, Path target, DependencySpec spec) {
        Path tmp = null;
        try {
            // Downloading next to the target keeps the move below on a single file system
            tmp = Files.createTempFile(LIBS_DIR, "download", ".jar");

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();

                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Download of " + url + " failed with status " + response.code());
                }

                try (BufferedSink sink = Okio.buffer(Okio.sink(tmp))) {
                    sink.writeAll(body.source());
                }
            }

            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download dependency " + spec.cacheKey(), e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove the temporary download {}: {}", path, e.getMessage());
        }
    }
}
