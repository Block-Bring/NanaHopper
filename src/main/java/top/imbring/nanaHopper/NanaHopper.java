package top.imbring.nanaHopper;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import top.imbring.nanaHopper.command.HopperCommand;
import top.imbring.nanaHopper.command.NanaHopperCommand;
import top.imbring.nanaHopper.config.ConfigMerger;
import top.imbring.nanaHopper.hopper.HopperEditor;
import top.imbring.nanaHopper.hopper.ManagedHoppers;
import top.imbring.nanaHopper.i18n.Messages;
import top.imbring.nanaHopper.listener.HopperBlockListener;
import top.imbring.nanaHopper.listener.HopperInteractListener;
import top.imbring.nanaHopper.listener.HopperPlaceListener;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class NanaHopper extends JavaPlugin {

    private ManagedHoppers managedHoppers;
    private Messages messages;

    @Override
    public void onEnable() {
        messages = Messages.load(this);

        managedHoppers = new ManagedHoppers(this);
        managedHoppers.scanLoadedChunks(getServer());

        BooleanSupplier refreshPanel = () -> getConfig().getBoolean("refresh-panel-after-command", true);
        HopperEditor hopperEditor = new HopperEditor(this, messages);

        getServer().getPluginManager().registerEvents(new HopperBlockListener(managedHoppers), this);
        getServer().getPluginManager().registerEvents(
            new HopperInteractListener(managedHoppers, messages, hopperEditor, refreshPanel), this);
        getServer().getPluginManager().registerEvents(new HopperPlaceListener(messages), this);

        HopperCommand hopperCommand = new HopperCommand(managedHoppers, messages, hopperEditor, refreshPanel);
        PluginCommand command = getCommand("hopper");
        if (command != null) {
            command.setExecutor(hopperCommand);
            command.setTabCompleter(hopperCommand);
        }

        NanaHopperCommand nanaHopperCommand = new NanaHopperCommand(this, messages);
        PluginCommand adminCommand = getCommand("nanahopper");
        if (adminCommand != null) {
            adminCommand.setExecutor(nanaHopperCommand);
            adminCommand.setTabCompleter(nanaHopperCommand);
        }

        // Paces managed hoppers whose speed differs from the vanilla default.
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> managedHoppers.tickPacedHoppers(), 1L, 1L);

        getServer().getConsoleSender().sendMessage(messages.message("nanahopper.console.enabled"));
    }

    @Override
    public void onDisable() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(this);
        getServer().getConsoleSender().sendMessage(messages.message("nanahopper.console.disabled"));
        getServer().getConsoleSender().sendMessage(messages.message("nanahopper.console.goodbye"));
    }

    /**
     * Reloads config.yml and the language files on demand ("/nanahopper
     * reload"). Both user files are first synchronized with their bundled
     * defaults, so missing keys are filled in at the correct position and
     * the key order follows the standard files.
     */
    public void reloadPlugin(CommandSender sender) {
        try {
            ConfigMerger.syncWithDefault(this, "config.yml", "config.yml");
            reloadConfig();

            for (String bundled : List.of("lang/locale_us.yml", "lang/locale_cn.yml")) {
                ConfigMerger.syncWithDefault(this, bundled, bundled);
            }
            // A custom language file is synchronized against the bundled
            // English template, which defines the standard key set and order.
            String active = Messages.languageFileName(this);
            if (!active.equals("lang/locale_us.yml") && !active.equals("lang/locale_cn.yml")) {
                ConfigMerger.syncWithDefault(this, "lang/locale_us.yml", active);
            }

            messages.reload(this);
            sender.sendMessage(messages.message("nanahopper.admin.reload.success"));
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("Failed to reload NanaHopper: " + e.getMessage());
            sender.sendMessage(messages.message("nanahopper.admin.reload.fail"));
        }
    }
}
