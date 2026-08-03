package de.itsjxsper.advancedreports.plugin.loader.cache;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import de.itsjxsper.advancedreports.plugin.AdvancedReportsPlugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


/**
 * Persists installed versions per dependency (cacheKey -> installed version) in libs/versions.json,
 * so no remote check is strictly required on every server start if a known version is already
 * installed locally.
 */
public class VersionCache {

    private final Path cacheFile;
    private final Gson gson = new Gson();
    private final Map<String, String> version;

    private final AdvancedReportsPlugin plugin = AdvancedReportsPlugin.getInstance();

    public VersionCache(@NonNull Path libsDir) {
        this.cacheFile = libsDir.resolve("version.json");
        this.version = load();
    }

    private @NonNull Map<String, String> load() {
        if (!Files.exists(this.cacheFile)) {
            return new HashMap<>();
        }


        try (Reader reader = Files.newBufferedReader(this.cacheFile)) {
            Type type = new TypeToken<Map<String, String>>() {
            }.getType();

            Map<String, String> result = gson.fromJson(reader, type);
            return result != null ? result : new HashMap<>();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load version cache: " + e.getMessage());
            return new HashMap<>();
        }
    }

    public String getInstalledVersion(String cacheKey) {
        return version.get(cacheKey);
    }

    public void setInstalledVersion(String cacheKey, String version) {
        this.version.put(cacheKey, version);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(cacheFile.getParent());

            try (Writer writer = Files.newBufferedWriter(cacheFile)) {
                gson.toJson(version, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save version cache: " + e.getMessage());
        }
    }
}
