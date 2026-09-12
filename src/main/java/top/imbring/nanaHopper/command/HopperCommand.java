package top.imbring.nanaHopper.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import top.imbring.nanaHopper.hopper.HopperEditor;
import top.imbring.nanaHopper.hopper.HopperPanel;
import top.imbring.nanaHopper.hopper.HopperTarget;
import top.imbring.nanaHopper.hopper.ManagedHoppers;
import top.imbring.nanaHopper.i18n.Messages;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Handles the functional "/hopper" command: "claim", "release" and "speed"
 * operate on the hopper the player is looking at. The "editor" subcommand
 * group manages the hopper editor item in hand: "true"/"false" convert and
 * restore the item, "claim"/"release"/"speed" edit the properties stored
 * in the editor item.
 */
public final class HopperCommand implements TabExecutor {

    private static final int REACH_DISTANCE = 5;

    private static final List<String> SUBCOMMANDS = List.of("claim", "release", "speed", "editor");
    private static final List<String> SPEED_SUGGESTIONS = List.of("reset", "+0.1", "-0.1");
    private static final List<String> EDITOR_SUGGESTIONS = List.of("true", "false", "claim", "release", "speed");

    private static final Component EMPTY_LINE = Component.empty();

    private final ManagedHoppers managedHoppers;
    private final Messages messages;
    private final HopperEditor hopperEditor;
    private final HopperPanel hopperPanel;
    private final BooleanSupplier refreshPanel;

    public HopperCommand(ManagedHoppers managedHoppers, Messages messages,
                         HopperEditor hopperEditor, BooleanSupplier refreshPanel) {
        this.managedHoppers = managedHoppers;
        this.messages = messages;
        this.hopperEditor = hopperEditor;
        this.hopperPanel = new HopperPanel(messages);
        this.refreshPanel = refreshPanel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.message("hopper.command.player-only"));
            return true;
        }
        if (args.length == 0 || args.length > 3) {
            sendUsage(player, label);
            return true;
        }
        if (args[0].equalsIgnoreCase("editor")) {
            editor(player, args, label);
            return true;
        }
        if (args.length > 2 || (args.length == 2 && !args[0].equalsIgnoreCase("speed"))) {
            sendUsage(player, label);
            return true;
        }

        HopperTarget target = resolveTarget(player);
        if (target == null) {
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim" -> claim(player, target);
            case "release" -> release(player, target);
            case "speed" -> speed(player, target, args.length == 2 ? args[1] : null);
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
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> suggestions;
            if (args[0].equalsIgnoreCase("speed")) {
                suggestions = SPEED_SUGGESTIONS;
            } else if (args[0].equalsIgnoreCase("editor")) {
                suggestions = EDITOR_SUGGESTIONS;
            } else {
                return List.of();
            }
            return suggestions.stream()
                .filter(suggestion -> suggestion.startsWith(input))
                .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("editor")
            && args[1].equalsIgnoreCase("speed")) {
            String input = args[2].toLowerCase();
            return SPEED_SUGGESTIONS.stream()
                .filter(suggestion -> suggestion.startsWith(input))
                .toList();
        }
        return List.of();
    }

    /** The operation target: the hopper block the player is looking at. */
    private HopperTarget resolveTarget(Player player) {
        Block target = player.getTargetBlockExact(REACH_DISTANCE);
        if (target == null || !(target.getState() instanceof Hopper hopper)) {
            player.sendMessage(messages.message("hopper.command.not-looking-at-hopper"));
            return null;
        }
        return HopperTarget.ofBlock(hopper, managedHoppers);
    }

    /**
     * Handles "/hopper editor ...": "true"/"false" convert and restore the
     * item in hand, "claim", "release" and "speed" edit the properties
     * stored in the editor item.
     */
    private void editor(Player player, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(player, label);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "true" -> convert(player, true);
            case "false" -> convert(player, false);
            case "claim", "release", "speed" -> editStoredProperties(player, args);
            default -> sendUsage(player, label);
        }
    }

    private void convert(Player player, boolean enable) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (enable && item.getType() == Material.AIR) {
            player.sendMessage(messages.message("hopper.editor.empty-hand"));
            return;
        }

        boolean changed = enable ? hopperEditor.convert(item) : hopperEditor.restore(item);
        String feedbackKey;
        if (enable) {
            feedbackKey = changed ? "hopper.editor.created" : "hopper.editor.already-editor";
        } else {
            feedbackKey = changed ? "hopper.editor.restored" : "hopper.editor.not-editor";
        }
        Component feedback = messages.message(feedbackKey);

        if (changed && enable) {
            // Show the panel of the fresh editor right away.
            sendPanelWithFeedback(player, HopperTarget.ofEditor(item, hopperEditor), feedback);
        } else {
            player.sendMessage(feedback);
        }
    }

    private void editStoredProperties(Player player, String[] args) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!hopperEditor.isEditor(item)) {
            player.sendMessage(messages.message("hopper.editor.not-editor"));
            return;
        }
        HopperTarget target = HopperTarget.ofEditor(item, hopperEditor);
        switch (args[1].toLowerCase()) {
            case "claim" -> claim(player, target);
            case "release" -> release(player, target);
            case "speed" -> speed(player, target, args.length == 3 ? args[2] : null);
        }
    }

    private void claim(Player player, HopperTarget target) {
        Component feedback = target.claim()
            ? messages.message("hopper.command.claim.success")
            : messages.message("hopper.command.claim.already-managed");
        sendPanelWithFeedback(player, target, feedback);
    }

    private void release(Player player, HopperTarget target) {
        Component feedback = target.release()
            ? messages.message("hopper.command.release.success")
            : messages.message("hopper.command.release.not-managed");
        sendPanelWithFeedback(player, target, feedback);
    }

    private void speed(Player player, HopperTarget target, String value) {
        if (!target.isManaged()) {
            sendPanelWithFeedback(player, target,
                messages.message("hopper.command.speed.not-managed"));
            return;
        }
        if (value == null) {
            double current = target.getSpeed();
            String key = current == ManagedHoppers.DEFAULT_SPEED
                ? "hopper.command.speed.current-default"
                : "hopper.command.speed.current";
            sendPanelWithFeedback(player, target,
                messages.message(key, "speed", String.valueOf(current)));
            return;
        }

        double newSpeed;
        if (value.equalsIgnoreCase("reset")) {
            newSpeed = ManagedHoppers.DEFAULT_SPEED;
        } else {
            try {
                if (value.startsWith("+") || value.startsWith("-")) {
                    double delta = Double.parseDouble(value);
                    newSpeed = target.getSpeed() + delta;
                } else {
                    newSpeed = Double.parseDouble(value);
                }
            } catch (NumberFormatException e) {
                sendPanelWithFeedback(player, target,
                    messages.message("hopper.command.speed.invalid-number",
                        "min", String.valueOf(ManagedHoppers.MIN_SPEED),
                        "max", String.valueOf(ManagedHoppers.MAX_SPEED)));
                return;
            }
            if (Double.isNaN(newSpeed) || newSpeed < ManagedHoppers.MIN_SPEED
                || newSpeed > ManagedHoppers.MAX_SPEED) {
                sendPanelWithFeedback(player, target,
                    messages.message("hopper.command.speed.out-of-range",
                        "min", String.valueOf(ManagedHoppers.MIN_SPEED),
                        "max", String.valueOf(ManagedHoppers.MAX_SPEED)));
                return;
            }
        }

        target.setSpeed(newSpeed);
        String feedbackKey = newSpeed == ManagedHoppers.DEFAULT_SPEED
            ? "hopper.command.speed.set-default"
            : "hopper.command.speed.set";
        sendPanelWithFeedback(player, target,
            messages.message(feedbackKey, "speed", String.valueOf(newSpeed)));
    }

    /**
     * Sends the operation feedback. When panel refresh is enabled, an empty
     * line and the updated management panel are shown before the feedback
     * message to simulate a TUI-like experience.
     */
    private void sendPanelWithFeedback(Player player, HopperTarget target, Component feedback) {
        if (refreshPanel.getAsBoolean()) {
            player.sendMessage(EMPTY_LINE);
            player.sendMessage(hopperPanel.render(target));
        }
        player.sendMessage(feedback);
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(messages.message("hopper.command.usage", "command", label));
    }
}
