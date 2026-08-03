package de.itsjxsper.advancedreports.plugin;

import de.itsjxsper.advancedreports.plugin.translation.TranslationManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdvancedReportsPlugin extends JavaPlugin {

    @Getter
    private static AdvancedReportsPlugin instance;

    @Getter
    private TranslationManager translationManager;

    @Override
    public void onEnable() {
        instance = this;

        this.translationManager = new TranslationManager(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
