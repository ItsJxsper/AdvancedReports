package de.itsjxsper.advancedreports.plugin.translation;

import de.itsjxsper.advancedreports.plugin.AdvancedReportsPlugin;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.ResourceBundle;

public class TranslationManager {

    private static final Key TRANSLATION_KEY = Key.key("advancedreports", "translations");

    private static final String BUNDLE_BASE_NAME = "i18n";

    @Getter
    private final MiniMessageTranslationStore store;

    private final AdvancedReportsPlugin plugin;

    public TranslationManager(AdvancedReportsPlugin plugin) {
        this.plugin = plugin;
        this.store = MiniMessageTranslationStore.create(TRANSLATION_KEY);

        saveTranslations();
        internationalize();
    }

    private void saveTranslations() {
        this.plugin.saveResource("translations/i18n_de_DE.properties", true);
        this.plugin.saveResource("translations/i18n_en_US.properties", true);
    }

    private void internationalize() {
        File folder = plugin.getDataFolder().toPath().resolve("translations").toFile();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".properties"));

        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".properties", "");

            if (!name.startsWith(BUNDLE_BASE_NAME)) continue;

            String suffix = "";
            if (name.length() > BUNDLE_BASE_NAME.length()) {
                suffix = name.substring(BUNDLE_BASE_NAME.length() + 1);
            }

            Locale locale;
            if (suffix.isEmpty()) {
                locale = Locale.getDefault();
            } else {
                String[] parts = suffix.split("_");
                if (parts.length >= 2) {
                    locale = Locale.forLanguageTag(parts[0] + "-" + parts[1]);
                } else {
                    locale = Locale.forLanguageTag(parts[0]);
                }
            }

            try {
                registerTranslations(locale);
            } catch (Exception e) {
                this.plugin.getLogger().warning("Unable to load translations for: " + name);
                this.plugin.getLogger().warning(e.getMessage());
            }
        }
    }

    private void registerTranslations(Locale locale) throws Exception {
        URL folderUrl = plugin.getDataFolder()
                .toPath()
                .resolve("translations")
                .toUri()
                .toURL();

        try (URLClassLoader loader = new URLClassLoader(new URL[]{folderUrl}, getClass().getClassLoader())) {
            ResourceBundle bundle = ResourceBundle.getBundle(
                    BUNDLE_BASE_NAME,   // → sucht i18n_de_DE.properties
                    locale,
                    loader
            );

            store.registerAll(locale, bundle, true);
            GlobalTranslator.translator().addSource(store);
        }
    }
}