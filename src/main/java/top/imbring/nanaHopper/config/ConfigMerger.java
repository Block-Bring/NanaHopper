package top.imbring.nanaHopper.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Synchronizes a user-editable YAML file with its bundled default resource.
 *
 * <p>The bundled resource defines the canonical structure: its key order and
 * comments are kept as-is, missing keys are filled in with default values at
 * the correct position (never appended blindly at the end), and values the
 * user already customized are preserved. Keys that only exist in the user
 * file are appended at the bottom so no custom data is lost.
 */
public final class ConfigMerger {

    private ConfigMerger() {
    }

    /**
     * Synchronizes the file at {@code targetPath} inside the plugin data
     * folder with the bundled resource at {@code resourcePath}.
     *
     * @param plugin       owning plugin, used to locate the bundled resource
     * @param resourcePath bundled resource, e.g. {@code config.yml} or
     *                     {@code lang/locale_us.yml}
     * @param targetPath   path of the user file relative to the data folder;
     *                     may differ from the resource path when a custom
     *                     file is synchronized against a bundled template
     */
    public static void syncWithDefault(JavaPlugin plugin, String resourcePath, String targetPath)
            throws IOException, InvalidConfigurationException {
        File target = new File(plugin.getDataFolder(), targetPath);
        if (!target.exists()) {
            // Nothing to merge; simply (re)create the file when it is bundled.
            if (resourcePath.equals(targetPath)) {
                plugin.saveResource(resourcePath, false);
            }
            return;
        }

        YamlConfiguration merged = loadResource(plugin, resourcePath);
        YamlConfiguration user = loadFile(target);

        // Keep the bundled key order; prefer the user's value where present.
        for (String path : merged.getKeys(true)) {
            if (merged.isConfigurationSection(path) || user.isConfigurationSection(path)) {
                continue;
            }
            if (user.contains(path)) {
                merged.set(path, user.get(path));
            }
        }

        // Preserve user-only keys by appending them after the standard block.
        for (String path : user.getKeys(true)) {
            if (user.isConfigurationSection(path) || merged.contains(path)) {
                continue;
            }
            merged.set(path, user.get(path));
        }

        merged.save(target);
    }

    private static YamlConfiguration loadResource(JavaPlugin plugin, String resourcePath)
            throws IOException, InvalidConfigurationException {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                throw new IOException("Bundled resource '" + resourcePath + "' not found in plugin jar");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.load(reader);
                return configuration;
            }
        }
    }

    private static YamlConfiguration loadFile(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        return configuration;
    }
}
