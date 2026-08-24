package top.imbring.nanaHopper;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import top.imbring.nanaHopper.command.HopperCommand;
import top.imbring.nanaHopper.hopper.ManagedHoppers;
import top.imbring.nanaHopper.i18n.Messages;
import top.imbring.nanaHopper.listener.HopperBlockListener;
import top.imbring.nanaHopper.listener.HopperPlaceListener;

public final class NanaHopper extends JavaPlugin {

    private ManagedHoppers managedHoppers;
    private Messages messages;

    @Override
    public void onEnable() {
        messages = Messages.load(this);

        managedHoppers = new ManagedHoppers(this);
        managedHoppers.scanLoadedChunks(getServer());

        getServer().getPluginManager().registerEvents(new HopperBlockListener(managedHoppers), this);
        getServer().getPluginManager().registerEvents(new HopperPlaceListener(messages), this);

        HopperCommand hopperCommand = new HopperCommand(managedHoppers, messages);
        PluginCommand command = getCommand("hopper");
        if (command != null) {
            command.setExecutor(hopperCommand);
            command.setTabCompleter(hopperCommand);
        }

        // Paces managed hoppers whose speed differs from the vanilla default.
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> managedHoppers.tickPacedHoppers(), 1L, 1L);

        getServer().getConsoleSender().sendMessage(messages.message("console.enabled"));
    }

    @Override
    public void onDisable() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(this);
        getServer().getConsoleSender().sendMessage(messages.message("console.disabled"));
        getServer().getConsoleSender().sendMessage(messages.message("console.goodbye"));
    }
}
