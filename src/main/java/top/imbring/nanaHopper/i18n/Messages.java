package top.imbring.nanaHopper.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Loads the configurable language file from the plugin data folder and builds
 * prefixed, colored message components from it.
 *
 * <p>Messages use legacy '&' color codes; placeholders are written as
 * {@code %name%} and substituted when the message is built.
 */
public final class Messages {

    public static final Component PREFIX = Component.text("[", NamedTextColor.AQUA)
        .append(Component.text("NanaHopper", NamedTextColor.GREEN))
        .append(Component.text("] ", NamedTextColor.AQUA));

    private static final String DEFAULT_LANGUAGE = "locale_us";
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final FileConfiguration messages;

    private Messages(FileConfiguration messages) {
        this.messages = messages;
    }

    /**
     * Copies the bundled language files into the data folder if absent, then
     * loads the one selected in config.yml.
     */
    public static Messages load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();

        String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE)
            .toLowerCase(Locale.ROOT);
        // Only allow simple file names to avoid path traversal.
        if (!language.matches("[a-z0-9_]+")) {
            language = DEFAULT_LANGUAGE;
        }

        saveResourceIfAbsent(plugin, "lang/locale_us.yml");
        saveResourceIfAbsent(plugin, "lang/locale_cn.yml");

        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file 'lang/" + language + ".yml' not found, falling back to '"
                + DEFAULT_LANGUAGE + "'.");
            file = new File(plugin.getDataFolder(), "lang/" + DEFAULT_LANGUAGE + ".yml");
        }

        FileConfiguration configuration = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().severe("Failed to load language file '" + file.getName()
                + "', messages will show as plain keys: " + e.getMessage());
        }
        return new Messages(configuration);
    }

    /**
     * Builds a prefixed message component for the given key.
     *
     * @param key          message key in the language file
     * @param replacements placeholder name / value pairs, e.g. "speed", "0.5"
     */
    public Component message(String key, String... replacements) {
        String raw = messages.getString(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return PREFIX.append(SERIALIZER.deserialize(raw));
    }

    private static void saveResourceIfAbsent(JavaPlugin plugin, String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }
}
