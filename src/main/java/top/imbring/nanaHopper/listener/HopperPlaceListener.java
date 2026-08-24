package top.imbring.nanaHopper.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import top.imbring.nanaHopper.i18n.Messages;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reminds players about the /hopper command the first time they place a
 * hopper after joining. The reminder is sent once per session and resets
 * when the player rejoins.
 */
public final class HopperPlaceListener implements Listener {

    private final Messages messages;

    /** Players who already received the reminder in their current session. */
    private final Set<UUID> remindedPlayers = ConcurrentHashMap.newKeySet();

    public HopperPlaceListener(Messages messages) {
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        Player player = event.getPlayer();
        // add() returns true only for the first placement in this session.
        if (remindedPlayers.add(player.getUniqueId())) {
            player.sendMessage(messages.message("reminder.hopper-place"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // New session: the player may receive the reminder again.
        remindedPlayers.remove(event.getPlayer().getUniqueId());
    }
}
