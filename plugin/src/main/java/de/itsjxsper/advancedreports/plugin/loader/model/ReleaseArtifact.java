package de.itsjxsper.advancedreports.plugin.loader.model;

/**
 * A resolved release asset: the artifact version and the URL its jar can be downloaded from.
 */
public record ReleaseArtifact(String version, String downloadUrl) {
}
