package de.itsjxsper.advancedreports.plugin;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdvancedReportsPlugin extends JavaPlugin {

    @Getter
    private static AdvancedReportsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        // Plugin startup logic
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
