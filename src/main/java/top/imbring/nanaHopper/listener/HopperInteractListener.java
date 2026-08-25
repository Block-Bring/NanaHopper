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

    private final ManagedHoppers managedHoppers;
    private final Messages messages;

    public HopperInteractListener(ManagedHoppers managedHoppers, Messages messages) {
        this.managedHoppers = managedHoppers;
        this.messages = messages;
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
        boolean managed = managedHoppers.isManaged(hopper.getLocation());
        double speed = managedHoppers.getSpeed(hopper);

        String statusKey = managed ? "panel.status.managed" : "panel.status.vanilla";
        String statusText = messages.raw(statusKey + ".status-text");
        String changeButton = messages.raw(statusKey + ".change-button");
        String speedStatus = messages.raw(speed == ManagedHoppers.DEFAULT_SPEED
            ? "panel.speed.vanilla" : "panel.speed.modified");

        player.sendMessage(messages.component("panel.template",
            "x", String.valueOf(hopper.getX()),
            "y", String.valueOf(hopper.getY()),
            "z", String.valueOf(hopper.getZ()),
            "status_text", statusText,
            "change_button", changeButton,
            "speed", String.valueOf(speed),
            "speed_status", speedStatus));
    }
}
