package top.imbring.nanaHopper.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import top.imbring.nanaHopper.hopper.HopperPanel;
import top.imbring.nanaHopper.hopper.ManagedHoppers;
import top.imbring.nanaHopper.i18n.Messages;

/**
 * Opens the hopper management panel when a player sneak + right-clicks a
 * hopper block with an empty hand.
 *
 * <p>The whole panel layout is defined by the {@code panel.template} entry in
 * the language file; this listener only picks the right status / speed parts
 * and fills in the placeholders before handing the template to
 * {@link Messages}.
 */
public final class HopperInteractListener implements Listener {

    private final HopperPanel hopperPanel;

    public HopperInteractListener(ManagedHoppers managedHoppers, Messages messages) {
        this.hopperPanel = new HopperPanel(managedHoppers, messages);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()
            || player.getInventory().getItemInMainHand().getType() != Material.AIR
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
        showPanel(player, hopper);
    }

    private void showPanel(Player player, Hopper hopper) {
        player.sendMessage(hopperPanel.render(hopper));
    }
}
