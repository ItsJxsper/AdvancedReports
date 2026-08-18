package de.itsjxsper.advancedreports.plugin.loader.model;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public record DependencySpec(Source source, String groupId, String artifactId, String repoUrl, String githubRepo,
                             String assetNamePattern) {

    @Contract(value = "_, _, _ -> new", pure = true)
    public static @NonNull DependencySpec maven(String groupId, String artifactId, String repoUrl) {
        return new DependencySpec(Source.MAVEN_CENTRAL, groupId, artifactId, repoUrl, null, null);
    }

    @Contract(value = "_, _ -> new", pure = true)
    public static @NonNull DependencySpec githubRelease(String githubRepo, String assetNamePattern) {
        return new DependencySpec(Source.GITHUB_RELEASE, null, null, null, githubRepo, assetNamePattern);
    }

    public String cacheKey() {
        return source == Source.MAVEN_CENTRAL ? groupId + ":" + artifactId : "github:" + githubRepo;
    }

    public enum Source {
        MAVEN_CENTRAL,
        GITHUB_RELEASE
    }
}
