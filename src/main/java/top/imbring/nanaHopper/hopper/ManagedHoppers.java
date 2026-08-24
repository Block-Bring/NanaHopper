package top.imbring.nanaHopper.hopper;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which hoppers are managed (claimed) by NanaHopper and paces their
 * transfer speed.
 *
 * <p>The claim flag and the speed are stored in the hopper's
 * {@link PersistentDataContainer}, so they persist with the chunk data and
 * disappear automatically when the hopper block is destroyed. Two in-memory
 * structures back the runtime behaviour: a claimed-location index for O(1)
 * membership lookups, and a pacing state map holding only claimed hoppers
 * whose speed differs from the vanilla default.
 *
 * <p>Speed pacing never moves items itself. Paced hoppers only get their
 * transfer cooldown adjusted every tick; the actual item movement, comparator
 * updates, redstone locking and item entity pickup are still performed by
 * vanilla code. Hoppers with the default speed are not touched at all.
 */
public final class ManagedHoppers {

    /** Vanilla transfers 1 item every 8 ticks. */
    public static final double DEFAULT_SPEED = 0.125;

    public static final double MIN_SPEED = 0.0;
    public static final double MAX_SPEED = 1.0;

    /** Cooldown value used to freeze a hopper whose speed is 0. */
    private static final int FREEZE_COOLDOWN = 1_000_000;
    private static final int FREEZE_THRESHOLD = 1_000;

    private final NamespacedKey managedKey;
    private final NamespacedKey speedKey;

    /** world uuid -> all claimed hopper locations in that world */
    private final Map<UUID, Set<Location>> claimed = new ConcurrentHashMap<>();

    /** claimed hoppers whose speed differs from the default, with pacing state */
    private final Map<Location, HopperRuntime> paced = new ConcurrentHashMap<>();

    public ManagedHoppers(JavaPlugin plugin) {
        this.managedKey = new NamespacedKey(plugin, "managed");
        this.speedKey = new NamespacedKey(plugin, "speed");
    }

    /** Marks the given hopper as managed by NanaHopper. */
    public boolean claim(Hopper hopper) {
        PersistentDataContainer pdc = hopper.getPersistentDataContainer();
        if (pdc.has(managedKey, PersistentDataType.BYTE)) {
            return false;
        }
        pdc.set(managedKey, PersistentDataType.BYTE, (byte) 1);
        hopper.update(true, false);

        Location location = hopper.getLocation();
        getOrCreateClaimed(location.getWorld().getUID()).add(location);
        if (getSpeed(hopper) != DEFAULT_SPEED) {
            paced.put(location, new HopperRuntime());
        }
        return true;
    }

    /** Removes the managed flag and the custom speed from the given hopper. */
    public boolean release(Hopper hopper) {
        PersistentDataContainer pdc = hopper.getPersistentDataContainer();
        if (!pdc.has(managedKey, PersistentDataType.BYTE)) {
            return false;
        }
        pdc.remove(managedKey);
        pdc.remove(speedKey);
        // Hand control back to vanilla immediately, clearing any pacing or
        // freeze cooldown we may have applied.
        hopper.setTransferCooldown(0);
        hopper.update(true, false);

        removeFromClaimed(hopper.getLocation());
        paced.remove(hopper.getLocation());
        return true;
    }

    /** Whether the hopper at the given location is managed by NanaHopper. */
    public boolean isManaged(Location location) {
        Set<Location> locations = claimed.get(location.getWorld().getUID());
        return locations != null && locations.contains(location);
    }

    /** The configured speed of the given hopper, in items per tick. */
    public double getSpeed(Hopper hopper) {
        Double speed = hopper.getPersistentDataContainer().get(speedKey, PersistentDataType.DOUBLE);
        return speed == null ? DEFAULT_SPEED : speed;
    }

    /** Sets the speed of the given hopper, in items per tick. */
    public void setSpeed(Hopper hopper, double speed) {
        hopper.getPersistentDataContainer().set(speedKey, PersistentDataType.DOUBLE, speed);
        hopper.update(true, false);

        Location location = hopper.getLocation();
        if (speed == DEFAULT_SPEED) {
            // Vanilla cooldown behaviour is already exactly this rate.
            paced.remove(location);
        } else {
            paced.put(location, new HopperRuntime());
        }
    }

    /**
     * Advances the pacing state of every managed hopper whose speed differs
     * from the vanilla default. Must be called once per tick.
     *
     * <p>The item movement itself is left to vanilla: this only rewrites the
     * hopper transfer cooldown so that vanilla moves happen at the configured
     * rate. No writes occur while the cooldown is already in sync.
     */
    public void tickPacedHoppers() {
        for (Map.Entry<Location, HopperRuntime> entry : paced.entrySet()) {
            tickPacedHopper(entry.getKey(), entry.getValue());
        }
    }

    private void tickPacedHopper(Location location, HopperRuntime runtime) {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        Block block = location.getBlock();
        if (!(block.getState() instanceof Hopper hopper)) {
            // Should have been cleaned up by the block listener already.
            removeFromClaimed(location);
            paced.remove(location);
            return;
        }

        double speed = getSpeed(hopper);
        if (speed == DEFAULT_SPEED) {
            // Speed was reset externally; stop pacing but keep the claim.
            paced.remove(location);
            return;
        }

        runtime.progress += speed;
        int cooldown = hopper.getTransferCooldown();

        if (speed == MIN_SPEED) {
            // Frozen: keep the cooldown far in the future.
            if (cooldown < FREEZE_THRESHOLD) {
                hopper.setTransferCooldown(FREEZE_COOLDOWN);
                hopper.update(true, false);
            }
            return;
        }

        if (runtime.progress >= 1.0) {
            runtime.progress -= 1.0;
            if (cooldown > 0) {
                hopper.setTransferCooldown(0);
                hopper.update(true, false);
            }
        } else {
            // Ticks until the next item is allowed to move. Vanilla decrements
            // the cooldown by 1 per tick, so this stays in sync on its own and
            // only needs to be rewritten right after a transfer or speed change.
            int ticksUntilTransfer = (int) Math.ceil((1.0 - runtime.progress) / speed);
            if (cooldown != ticksUntilTransfer) {
                hopper.setTransferCooldown(ticksUntilTransfer);
                hopper.update(true, false);
            }
        }
    }

    /** Scans a chunk and rebuilds its cached claimed hoppers from PDC data. */
    public void scanChunk(Chunk chunk) {
        Set<Location> locations = getOrCreateClaimed(chunk.getWorld().getUID());
        locations.removeIf(location -> location.getChunk().equals(chunk));
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Hopper hopper
                && hopper.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE)) {
                Location location = hopper.getLocation();
                locations.add(location);
                if (getSpeed(hopper) != DEFAULT_SPEED) {
                    paced.put(location, new HopperRuntime());
                } else {
                    paced.remove(location);
                }
            }
        }
    }

    /** Drops the cached entries of an unloaded chunk; PDC data stays in the chunk. */
    public void unloadChunk(Chunk chunk) {
        Set<Location> locations = claimed.get(chunk.getWorld().getUID());
        if (locations != null) {
            locations.removeIf(location -> location.getChunk().equals(chunk));
        }
        paced.keySet().removeIf(location -> location.getChunk().equals(chunk));
    }

    /** Drops the cached entries of a block that no longer holds the claim flag. */
    public void forget(Block block) {
        Location location = block.getLocation();
        removeFromClaimed(location);
        paced.remove(location);
    }

    /** Drops all cached entries of an unloaded world. */
    public void unloadWorld(UUID worldId) {
        claimed.remove(worldId);
        paced.keySet().removeIf(location -> {
            World world = location.getWorld();
            return world != null && world.getUID().equals(worldId);
        });
    }

    /** Scans all currently loaded chunks, used on plugin enable. */
    public void scanLoadedChunks(Server server) {
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    private Set<Location> getOrCreateClaimed(UUID worldId) {
        return claimed.computeIfAbsent(worldId, id -> ConcurrentHashMap.newKeySet());
    }

    private void removeFromClaimed(Location location) {
        Set<Location> locations = claimed.get(location.getWorld().getUID());
        if (locations != null) {
            locations.remove(location);
        }
    }

    /** Mutable pacing state of a single paced hopper. */
    private static final class HopperRuntime {

        /** Fraction of an item accumulated towards the next transfer. */
        private double progress;
    }
}
