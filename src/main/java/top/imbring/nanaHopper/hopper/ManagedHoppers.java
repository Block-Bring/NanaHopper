package top.imbring.nanaHopper.hopper;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * disappear automatically when the hopper block is destroyed. The in-memory
 * cache is grouped by chunk: every loaded chunk owns one bucket holding the
 * claimed-location index for O(1) membership lookups plus the pacing state of
 * the hoppers whose speed differs from the vanilla default. A bucket only
 * exists while its chunk is loaded, so the per-tick scan never has to touch
 * unloaded chunks and unloading drops the whole bucket in O(1).
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

    /** world uuid -> chunk key -> cached entries of that loaded chunk */
    private final Map<UUID, Map<Long, ChunkBucket>> chunks = new ConcurrentHashMap<>();

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
        ChunkBucket bucket = bucketFor(location);
        bucket.claimed.add(location);
        if (getSpeed(hopper) != DEFAULT_SPEED) {
            bucket.paced.put(location, new HopperRuntime(location));
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

        Location location = hopper.getLocation();
        ChunkBucket bucket = bucketOf(location);
        if (bucket != null) {
            bucket.claimed.remove(location);
            bucket.paced.remove(location);
        }
        return true;
    }

    /** Whether the hopper at the given location is managed by NanaHopper. */
    public boolean isManaged(Location location) {
        ChunkBucket bucket = bucketOf(location);
        return bucket != null && bucket.claimed.contains(location);
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
        ChunkBucket bucket = bucketFor(location);
        if (speed == DEFAULT_SPEED) {
            // Vanilla cooldown behaviour is already exactly this rate.
            bucket.paced.remove(location);
        } else {
            bucket.paced.put(location, new HopperRuntime(location));
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
        for (Map<Long, ChunkBucket> worldChunks : chunks.values()) {
            for (ChunkBucket bucket : worldChunks.values()) {
                // Most loaded chunks have no paced hopper; skip them before
                // paying for the loaded-chunk lookup.
                if (bucket.paced.isEmpty()
                    || !bucket.world.isChunkLoaded(bucket.chunkX, bucket.chunkZ)) {
                    continue;
                }
                for (Map.Entry<Location, HopperRuntime> entry : bucket.paced.entrySet()) {
                    tickPacedHopper(bucket, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void tickPacedHopper(ChunkBucket bucket, Location location, HopperRuntime runtime) {
        Block block = bucket.world.getBlockAt(runtime.x, runtime.y, runtime.z);
        if (block.getType() != Material.HOPPER) {
            // Should have been cleaned up by the block listener already.
            bucket.claimed.remove(location);
            bucket.paced.remove(location);
            return;
        }
        // Live state: reads and writes go straight to the real block entity
        // instead of a full snapshot copy, and no update() is needed.
        Hopper hopper = (Hopper) block.getState(false);

        double speed = getSpeed(hopper);
        if (speed == DEFAULT_SPEED) {
            // Speed was reset externally; stop pacing but keep the claim.
            bucket.paced.remove(location);
            return;
        }

        runtime.progress += speed;
        int cooldown = hopper.getTransferCooldown();

        if (speed == MIN_SPEED) {
            // Frozen: keep the cooldown far in the future.
            if (cooldown < FREEZE_THRESHOLD) {
                hopper.setTransferCooldown(FREEZE_COOLDOWN);
            }
            return;
        }

        if (runtime.progress >= 1.0) {
            runtime.progress -= 1.0;
            if (cooldown > 0) {
                hopper.setTransferCooldown(0);
            }
        } else {
            // Ticks until the next item is allowed to move. Vanilla decrements
            // the cooldown by 1 per tick, so this stays in sync on its own and
            // only needs to be rewritten right after a transfer or speed change.
            int ticksUntilTransfer = (int) Math.ceil((1.0 - runtime.progress) / speed);
            if (cooldown != ticksUntilTransfer) {
                hopper.setTransferCooldown(ticksUntilTransfer);
            }
        }
    }

    /** Scans a chunk and rebuilds its cached claimed hoppers from PDC data. */
    public void scanChunk(Chunk chunk) {
        ChunkBucket bucket = new ChunkBucket(chunk.getWorld(), chunk.getX(), chunk.getZ());
        // Publish the bucket before filling it so a concurrent claim of a
        // hopper in this chunk lands in the same bucket instead of being lost.
        worldChunks(chunk.getWorld().getUID()).put(chunkKey(chunk.getX(), chunk.getZ()), bucket);

        for (BlockState state : chunk.getTileEntities(
            block -> block.getType() == Material.HOPPER, false)) {
            if (!(state instanceof Hopper hopper)
                || !hopper.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE)) {
                continue;
            }
            Location location = hopper.getLocation();
            bucket.claimed.add(location);
            if (getSpeed(hopper) != DEFAULT_SPEED) {
                bucket.paced.put(location, new HopperRuntime(location));
            }
        }
    }

    /** Drops the cached entries of an unloaded chunk; PDC data stays in the chunk. */
    public void unloadChunk(Chunk chunk) {
        Map<Long, ChunkBucket> worldChunks = chunks.get(chunk.getWorld().getUID());
        if (worldChunks != null) {
            worldChunks.remove(chunkKey(chunk.getX(), chunk.getZ()));
        }
    }

    /** Drops the cached entries of a block that no longer holds the claim flag. */
    public void forget(Block block) {
        Location location = block.getLocation();
        ChunkBucket bucket = bucketOf(location);
        if (bucket != null) {
            bucket.claimed.remove(location);
            bucket.paced.remove(location);
        }
    }

    /** Drops all cached entries of an unloaded world. */
    public void unloadWorld(UUID worldId) {
        chunks.remove(worldId);
    }

    /** Scans all currently loaded chunks, used on plugin enable. */
    public void scanLoadedChunks(Server server) {
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    private ChunkBucket bucketOf(Location location) {
        World world = location.getWorld();
        Map<Long, ChunkBucket> worldChunks = chunks.get(world.getUID());
        return worldChunks == null ? null
            : worldChunks.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    private ChunkBucket bucketFor(Location location) {
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return worldChunks(world.getUID()).computeIfAbsent(
            chunkKey(chunkX, chunkZ),
            key -> new ChunkBucket(world, chunkX, chunkZ));
    }

    private Map<Long, ChunkBucket> worldChunks(UUID worldId) {
        return chunks.computeIfAbsent(worldId, id -> new ConcurrentHashMap<>());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Cached entries of one loaded chunk. */
    private static final class ChunkBucket {

        private final World world;
        private final int chunkX;
        private final int chunkZ;

        /** All claimed hopper locations in this chunk. */
        private final Set<Location> claimed = ConcurrentHashMap.newKeySet();

        /** Claimed hoppers whose speed differs from the default, with pacing state. */
        private final Map<Location, HopperRuntime> paced = new ConcurrentHashMap<>();

        private ChunkBucket(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    /** Mutable pacing state of a single paced hopper. */
    private static final class HopperRuntime {

        private final int x;
        private final int y;
        private final int z;

        /** Fraction of an item accumulated towards the next transfer. */
        private double progress;

        private HopperRuntime(Location location) {
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }
    }
}
