package top.imbring.nanaHopper.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import top.imbring.nanaHopper.hopper.ManagedHoppers;

/**
 * Keeps the managed hopper cache in sync with world changes:
 * chunks being loaded/unloaded and hopper blocks being destroyed.
 */
public final class HopperBlockListener implements Listener {

    private final ManagedHoppers managedHoppers;

    public HopperBlockListener(ManagedHoppers managedHoppers) {
        this.managedHoppers = managedHoppers;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        managedHoppers.scanChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        managedHoppers.unloadChunk(event.getChunk());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        managedHoppers.unloadWorld(event.getWorld().getUID());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        forgetIfHopper(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::forgetIfHopper);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::forgetIfHopper);
    }

    /**
     * Once a hopper block is destroyed, its PDC claim flag is gone with it;
     * only the in-memory cache entry still needs to be dropped.
     */
    private void forgetIfHopper(Block block) {
        if (block.getType() == Material.HOPPER) {
            managedHoppers.forget(block);
        }
    }
}
