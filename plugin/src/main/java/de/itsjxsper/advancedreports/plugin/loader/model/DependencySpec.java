package de.itsjxsper.advancedreports.plugin.loader.model;

import java.util.regex.Pattern;

/**
 * Identifies a single artifact distributed through the GitHub Releases of a repository.
 *
 * <p>Both the release tag and the release asset follow a fixed naming convention derived from the
 * artifact id, so the artifact id alone is enough to locate a jar in a monorepo whose releases are
 * shared between several modules.
 */
public record DependencySpec(String githubRepo, String artifactId) {

    /**
     * Matches the release tags belonging to this artifact, capturing the version.
     * Tag convention: {@code <artifactId>-v<version>}, e.g. {@code common-v0.00.5}.
     */
    public Pattern tagPattern() {
        return Pattern.compile("^" + Pattern.quote(artifactId) + "-v(.+)$");
    }

    /**
     * Name of the release asset and of the local copy in the libs directory.
     * Convention: {@code <artifactId>-<version>.jar}, e.g. {@code common-0.00.5.jar}.
     */
    public String jarFileName(String version) {
        return artifactId + "-" + version + ".jar";
    }

    /**
     * Key under which the installed version is recorded. Includes the artifact id, because a single
     * repository publishes several artifacts and they must not share a cache entry.
     */
    public String cacheKey() {
        return "github:" + githubRepo + ":" + artifactId;
    }
}
