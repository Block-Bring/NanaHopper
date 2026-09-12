package top.imbring.nanaHopper.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import top.imbring.nanaHopper.hopper.HopperEditor;
import top.imbring.nanaHopper.hopper.HopperPanel;
import top.imbring.nanaHopper.hopper.HopperTarget;
import top.imbring.nanaHopper.hopper.ManagedHoppers;
import top.imbring.nanaHopper.i18n.Messages;

import java.util.function.BooleanSupplier;

/**
 * Handles right-clicks around hoppers:
 *
 * <ul>
 *   <li>Sneak + right-click a hopper with empty hands opens its panel.</li>
 *   <li>Right-clicking with a hopper editor item opens the panel of the
 *       properties stored in the item.</li>
 *   <li>Sneak + right-click a hopper with a hopper editor item applies the
 *       stored properties to that hopper.</li>
 * </ul>
 */
public final class HopperInteractListener implements Listener {

    private static final Component EMPTY_LINE = Component.empty();

    private final ManagedHoppers managedHoppers;
    private final Messages messages;
    private final HopperEditor hopperEditor;
    private final HopperPanel hopperPanel;
    private final BooleanSupplier refreshPanel;

    public HopperInteractListener(ManagedHoppers managedHoppers, Messages messages,
                                  HopperEditor hopperEditor, BooleanSupplier refreshPanel) {
        this.managedHoppers = managedHoppers;
        this.messages = messages;
        this.hopperEditor = hopperEditor;
        this.hopperPanel = new HopperPanel(messages);
        this.refreshPanel = refreshPanel;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hopperEditor.isEditor(hand)) {
            handleEditor(player, hand, event);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK
            || !player.isSneaking()
            || hand.getType() != Material.AIR
            || player.getInventory().getItemInOffHand().getType() != Material.AIR) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.HOPPER
            || !(block.getState() instanceof Hopper hopper)) {
            return;
        }

        // Keep the vanilla hopper inventory closed while the panel is shown.
        event.setCancelled(true);
        player.sendMessage(hopperPanel.render(HopperTarget.ofBlock(hopper, managedHoppers)));
    }

    private void handleEditor(Player player, ItemStack editor, PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()
            && block != null && block.getState() instanceof Hopper hopper) {
            // Apply the stored properties to the hopper.
            event.setCancelled(true);
            hopperEditor.applyTo(editor, hopper, managedHoppers);
            if (refreshPanel.getAsBoolean()) {
                player.sendMessage(EMPTY_LINE);
                player.sendMessage(hopperPanel.render(HopperTarget.ofBlock(hopper, managedHoppers)));
            }
            player.sendMessage(messages.message("hopper.editor.applied"));
            return;
        }

        // The editor is a tool item: any other right-click shows the panel
        // of its stored properties instead of using the item or the block.
        event.setCancelled(true);
        player.sendMessage(hopperPanel.render(HopperTarget.ofEditor(editor, hopperEditor)));
    }
}
