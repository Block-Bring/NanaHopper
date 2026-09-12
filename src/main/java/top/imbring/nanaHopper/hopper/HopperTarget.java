package top.imbring.nanaHopper.hopper;

import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.ItemStack;

/**
 * An editable source of hopper properties: either a real hopper block in
 * the world or a hopper editor item. Lets the management panel and the
 * "/hopper" subcommands operate on both without caring where the values
 * are stored.
 */
public interface HopperTarget {

    boolean isManaged();

    double getSpeed();

    /** Marks the target as managed; false if it already was. */
    boolean claim();

    /** Hands control back to vanilla; false if it was not managed. */
    boolean release();

    void setSpeed(double speed);

    /** Value for the {target} placeholder of the panel template. */
    String targetLabel();

    /** Position of the underlying hopper block, null for editor items. */
    Location location();

    static HopperTarget ofBlock(Hopper hopper, ManagedHoppers managedHoppers) {
        return new BlockTarget(hopper, managedHoppers);
    }

    static HopperTarget ofEditor(ItemStack item, HopperEditor editor) {
        return new EditorTarget(item, editor);
    }

    /** A real hopper block, backed by the chunk PDC via ManagedHoppers. */
    final class BlockTarget implements HopperTarget {

        private final Hopper hopper;
        private final ManagedHoppers managedHoppers;

        private BlockTarget(Hopper hopper, ManagedHoppers managedHoppers) {
            this.hopper = hopper;
            this.managedHoppers = managedHoppers;
        }

        @Override
        public boolean isManaged() {
            return managedHoppers.isManaged(hopper.getLocation());
        }

        @Override
        public double getSpeed() {
            return managedHoppers.getSpeed(hopper);
        }

        @Override
        public boolean claim() {
            return managedHoppers.claim(hopper);
        }

        @Override
        public boolean release() {
            return managedHoppers.release(hopper);
        }

        @Override
        public void setSpeed(double speed) {
            managedHoppers.setSpeed(hopper, speed);
        }

        @Override
        public String targetLabel() {
            Location location = hopper.getLocation();
            return location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
        }

        @Override
        public Location location() {
            return hopper.getLocation();
        }
    }

    /** A hopper editor item, backed by the item's own PDC. */
    final class EditorTarget implements HopperTarget {

        private final ItemStack item;
        private final HopperEditor editor;

        private EditorTarget(ItemStack item, HopperEditor editor) {
            this.item = item;
            this.editor = editor;
        }

        @Override
        public boolean isManaged() {
            return editor.isManaged(item);
        }

        @Override
        public double getSpeed() {
            return editor.getSpeed(item);
        }

        @Override
        public boolean claim() {
            return editor.setManaged(item, true);
        }

        @Override
        public boolean release() {
            return editor.setManaged(item, false);
        }

        @Override
        public void setSpeed(double speed) {
            editor.setSpeed(item, speed);
        }

        @Override
        public String targetLabel() {
            return editor.itemLabel(item);
        }

        @Override
        public Location location() {
            return null;
        }
    }
}
