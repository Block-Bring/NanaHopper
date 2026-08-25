package top.imbring.nanaHopper.command;

import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import top.imbring.nanaHopper.hopper.ManagedHoppers;
import top.imbring.nanaHopper.i18n.Messages;

import java.util.List;

/**
 * Handles "/hopper claim", "/hopper release" and "/hopper speed" for the
 * hopper the player is looking at.
 */
public final class HopperCommand implements TabExecutor {

    private static final int REACH_DISTANCE = 5;

    private static final List<String> SUBCOMMANDS = List.of("claim", "release", "speed");
    private static final List<String> SPEED_SUGGESTIONS = List.of("reset", "+0.1", "-0.1");

    private final ManagedHoppers managedHoppers;
    private final Messages messages;

    public HopperCommand(ManagedHoppers managedHoppers, Messages messages) {
        this.managedHoppers = managedHoppers;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.message("command.player-only"));
            return true;
        }
        if (args.length == 0 || args.length > 2 || (args.length == 2 && !args[0].equalsIgnoreCase("speed"))) {
            sendUsage(player, label);
            return true;
        }

        Block target = player.getTargetBlockExact(REACH_DISTANCE);
        if (target == null || !(target.getState() instanceof Hopper hopper)) {
            player.sendMessage(messages.message("command.not-looking-at-hopper"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim" -> claim(player, hopper);
            case "release" -> release(player, hopper);
            case "speed" -> speed(player, hopper, args.length == 2 ? args[1] : null);
            default -> sendUsage(player, label);
        }
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
        if (args.length == 2 && args[0].equalsIgnoreCase("speed")) {
            String input = args[1].toLowerCase();
            return SPEED_SUGGESTIONS.stream()
                .filter(s -> s.startsWith(input))
                .toList();
        }
        return List.of();
    }

    private void claim(Player player, Hopper hopper) {
        if (!managedHoppers.claim(hopper)) {
            player.sendMessage(messages.message("command.claim.already-managed"));
            return;
        }
        player.sendMessage(messages.message("command.claim.success"));
    }

    private void release(Player player, Hopper hopper) {
        if (!managedHoppers.release(hopper)) {
            player.sendMessage(messages.message("command.release.not-managed"));
            return;
        }
        player.sendMessage(messages.message("command.release.success"));
    }

    private void speed(Player player, Hopper hopper, String value) {
        if (!managedHoppers.isManaged(hopper.getLocation())) {
            player.sendMessage(messages.message("command.speed.not-managed"));
            return;
        }
        if (value == null) {
            double current = managedHoppers.getSpeed(hopper);
            String key = current == ManagedHoppers.DEFAULT_SPEED
                ? "command.speed.current-default"
                : "command.speed.current";
            player.sendMessage(messages.message(key, "speed", String.valueOf(current)));
            return;
        }

        double newSpeed;
        if (value.equalsIgnoreCase("reset")) {
            newSpeed = ManagedHoppers.DEFAULT_SPEED;
        } else {
            try {
                if (value.startsWith("+") || value.startsWith("-")) {
                    double delta = Double.parseDouble(value);
                    newSpeed = managedHoppers.getSpeed(hopper) + delta;
                } else {
                    newSpeed = Double.parseDouble(value);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(messages.message("command.speed.invalid-number",
                    "min", String.valueOf(ManagedHoppers.MIN_SPEED),
                    "max", String.valueOf(ManagedHoppers.MAX_SPEED)));
                return;
            }
            if (Double.isNaN(newSpeed) || newSpeed < ManagedHoppers.MIN_SPEED
                || newSpeed > ManagedHoppers.MAX_SPEED) {
                player.sendMessage(messages.message("command.speed.out-of-range",
                    "min", String.valueOf(ManagedHoppers.MIN_SPEED),
                    "max", String.valueOf(ManagedHoppers.MAX_SPEED)));
                return;
            }
        }

        managedHoppers.setSpeed(hopper, newSpeed);
        String feedbackKey = newSpeed == ManagedHoppers.DEFAULT_SPEED
            ? "command.speed.set-default"
            : "command.speed.set";
        player.sendMessage(messages.message(feedbackKey, "speed", String.valueOf(newSpeed)));
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(messages.message("command.usage", "command", label));
    }
}
