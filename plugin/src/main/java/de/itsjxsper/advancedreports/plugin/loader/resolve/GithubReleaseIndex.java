package de.itsjxsper.advancedreports.plugin.loader.resolve;

import com.google.gson.*;
import de.itsjxsper.advancedreports.plugin.loader.model.DependencySpec;
import de.itsjxsper.advancedreports.plugin.loader.model.ReleaseArtifact;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the releases of a single GitHub repository and resolves the newest release belonging to a
 * given artifact.
 *
 * <p>A monorepo publishes the releases of all of its modules to one repository, interleaved, so the
 * {@code /releases/latest} endpoint cannot be used: it returns a single release for the whole
 * repository, which belongs to whichever module was released last. Instead the full release list is
 * read once and filtered per artifact by its tag prefix.
 *
 * <p>The list is fetched lazily and kept for the lifetime of this instance, so resolving several
 * artifacts of the same repository costs one request rather than one per artifact. That matters
 * because the loader talks to the GitHub API unauthenticated, which allows 60 requests per hour and
 * IP address.
 *
 * <p>Only the first page of releases is read. With the current release cadence that covers the whole
 * history several times over, but an artifact whose newest release fell off that page would become
 * invisible.
 */
@Slf4j
public class GithubReleaseIndex {

    private static final String USER_AGENT = "AdvancedReports-PluginLoader";
    private static final int PAGE_SIZE = 100;

    private final OkHttpClient httpClient;
    private final String githubRepo;
    private final Gson gson = new Gson();

    private List<JsonObject> releases;
    private boolean fetched;

    public GithubReleaseIndex(OkHttpClient httpClient, String githubRepo) {
        this.httpClient = httpClient;
        this.githubRepo = githubRepo;
    }

    private static Instant readPublishedAt(JsonObject release) {
        String publishedAt = readString(release, "published_at");
        if (publishedAt == null) {
            return Instant.EPOCH;
        }

        try {
            return Instant.parse(publishedAt);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    private static @Nullable String readString(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static boolean readBoolean(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    /**
     * Resolves the newest published release of the given artifact and the jar asset attached to it.
     * Drafts and pre-releases are ignored, so a pre-release is never rolled out to a live server.
     *
     * @return the resolved artifact, or empty if the release list could not be read, no release
     * belongs to this artifact, or the matching release carries no jar asset
     */
    public Optional<ReleaseArtifact> latestFor(@NonNull DependencySpec spec) {
        List<JsonObject> all = releases();
        if (all.isEmpty()) {
            // The reason was already logged while fetching, so do not report it a second time here
            return Optional.empty();
        }

        Pattern tagPattern = spec.tagPattern();

        Optional<VersionedRelease> newest = all.stream()
                .filter(release -> !readBoolean(release, "draft") && !readBoolean(release, "prerelease"))
                .map(release -> toVersionedRelease(release, tagPattern))
                .filter(Objects::nonNull)
                .max(Comparator.comparing(VersionedRelease::publishedAt));

        if (newest.isEmpty()) {
            log.warn("No published release of {} matches the tag pattern {}", githubRepo, tagPattern.pattern());
            return Optional.empty();
        }

        String version = newest.get().version();
        String assetName = spec.jarFileName(version);
        String downloadUrl = findAssetUrl(newest.get().release(), assetName);

        if (downloadUrl == null) {
            log.warn("Release {} of {} carries no asset named {}",
                    readString(newest.get().release(), "tag_name"), githubRepo, assetName);
            return Optional.empty();
        }

        return Optional.of(new ReleaseArtifact(version, downloadUrl));
    }

    private @Nullable VersionedRelease toVersionedRelease(JsonObject release, Pattern tagPattern) {
        String tag = readString(release, "tag_name");
        if (tag == null) {
            return null;
        }

        Matcher matcher = tagPattern.matcher(tag);
        if (!matcher.matches()) {
            return null;
        }

        return new VersionedRelease(matcher.group(1), readPublishedAt(release), release);
    }

    private @Nullable String findAssetUrl(@NonNull JsonObject release, String assetName) {
        JsonElement assets = release.get("assets");
        if (assets == null || !assets.isJsonArray()) {
            return null;
        }

        for (JsonElement element : assets.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject asset = element.getAsJsonObject();
            if (assetName.equals(readString(asset, "name"))) {
                return readString(asset, "browser_download_url");
            }
        }
        return null;
    }

    private List<JsonObject> releases() {
        if (!fetched) {
            fetched = true;
            releases = fetchReleases();
        }
        return releases;
    }

    private List<JsonObject> fetchReleases() {
        String url = "https://api.github.com/repos/" + githubRepo + "/releases?per_page=" + PAGE_SIZE;

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT) // GitHub API requires a User-Agent header
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();

            if (!response.isSuccessful() || body == null) {
                String rateLimitRemaining = response.header("X-RateLimit-Remaining");
                log.warn("GitHub API request for the releases of {} returned {}{}",
                        githubRepo,
                        response.code(),
                        rateLimitRemaining != null ? " (rate limit remaining: " + rateLimitRemaining + ")" : "");
                return List.of();
            }

            JsonArray array = gson.fromJson(body.charStream(), JsonArray.class);
            if (array == null) {
                log.warn("GitHub API returned an empty release list for {}", githubRepo);
                return List.of();
            }

            List<JsonObject> parsed = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    parsed.add(element.getAsJsonObject());
                }
            }
            return parsed;
        } catch (IOException | JsonParseException e) {
            log.warn("Could not read the releases of {}: {} - {}",
                    githubRepo, e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    private record VersionedRelease(String version, Instant publishedAt, JsonObject release) {
    }
}
