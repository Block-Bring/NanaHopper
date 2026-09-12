package top.imbring.nanaHopper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import top.imbring.nanaHopper.NanaHopper;
import top.imbring.nanaHopper.i18n.Messages;

import java.util.List;

/**
 * Handles "/nanahopper reload", the plugin management command. This is
 * separate from the functional "/hopper" command: "/nanahopper" operates on
 * the plugin itself, "/hopper" operates on hoppers.
 */
public final class NanaHopperCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload");

    private final NanaHopper plugin;
    private final Messages messages;

    public NanaHopperCommand(NanaHopper plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(messages.message("nanahopper.admin.usage", "command", label));
            return true;
        }
        plugin.reloadPlugin(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                .filter(subcommand -> subcommand.startsWith(input))
                .toList();
        }
        return List.of();
    }
}
