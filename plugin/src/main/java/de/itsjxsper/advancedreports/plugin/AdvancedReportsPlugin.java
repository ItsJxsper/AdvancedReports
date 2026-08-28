package de.itsjxsper.advancedreports.plugin;

import de.itsjxsper.advancedreports.api.client.AdvancedReportsApi;
import de.itsjxsper.advancedreports.api.client.AdvancedReportsApiConfig;
import de.itsjxsper.advancedreports.plugin.translation.TranslationManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class AdvancedReportsPlugin extends JavaPlugin {

    @Getter
    private static AdvancedReportsPlugin instance;

    @Getter
    private TranslationManager translationManager;

    @Getter
    private AdvancedReportsApi advancedReportsApi;

    private AdvancedReportsApiConfig apiConfig;

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        this.translationManager = new TranslationManager(this);

        this.apiConfig = AdvancedReportsApiConfig.builder()
                .baseUrl(this.getConfig().getString("api.base-url"))
                .serverUuid(UUID.fromString(Objects.requireNonNull(this.getConfig().getString("api.server-uuid"))))
                .build();

        this.advancedReportsApi = new AdvancedReportsApi(this.apiConfig);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.advancedReportsApi.shutdown();
    }
}
