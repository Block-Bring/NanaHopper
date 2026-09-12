package top.imbring.nanaHopper.hopper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import top.imbring.nanaHopper.i18n.Messages;

/**
 * Turns any item into a reusable hopper property editor.
 *
 * <p>An editor item stores a managed flag and a transfer speed in its own
 * {@link PersistentDataContainer}, editable through the same panel and
 * "/hopper" subcommands used for real hoppers. Sneak + right-clicking a
 * hopper block then applies the stored properties to it, so large setups
 * only need to be configured once. "/hopper editor true/false" converts
 * items into editors and restores them.
 */
public final class HopperEditor {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final NamespacedKey editorKey;
    private final NamespacedKey managedKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey originalNameKey;
    private final Messages messages;

    public HopperEditor(JavaPlugin plugin, Messages messages) {
        this.editorKey = new NamespacedKey(plugin, "editor");
        this.managedKey = new NamespacedKey(plugin, "editor-managed");
        this.speedKey = new NamespacedKey(plugin, "editor-speed");
        this.originalNameKey = new NamespacedKey(plugin, "editor-original-name");
        this.messages = messages;
    }

    /** Whether the given item is currently a hopper editor. */
    public boolean isEditor(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(editorKey, PersistentDataType.BYTE);
    }

    /**
     * Converts the item into an editor with default properties (managed,
     * vanilla default speed) and renames it. The original display name is
     * kept so it can be restored later.
     *
     * @return false if the item is already an editor or has no item meta
     */
    public boolean convert(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(editorKey, PersistentDataType.BYTE)) {
            return false;
        }

        Component originalName = meta.displayName();
        if (originalName != null) {
            pdc.set(originalNameKey, PersistentDataType.STRING, GSON.serialize(originalName));
        }
        pdc.set(editorKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(managedKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(speedKey, PersistentDataType.DOUBLE, ManagedHoppers.DEFAULT_SPEED);
        meta.displayName(MINI_MESSAGE.deserialize(messages.raw("hopper.editor.item-name")));
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Restores the item to its original state, including the display name
     * it had before being converted.
     *
     * @return false if the item is not an editor
     */
    public boolean restore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(editorKey, PersistentDataType.BYTE)) {
            return false;
        }

        pdc.remove(editorKey);
        pdc.remove(managedKey);
        pdc.remove(speedKey);
        String originalName = pdc.get(originalNameKey, PersistentDataType.STRING);
        if (originalName != null) {
            meta.displayName(GSON.deserialize(originalName));
            pdc.remove(originalNameKey);
        } else {
            meta.displayName(null);
        }
        item.setItemMeta(meta);
        return true;
    }

    /** The managed flag stored in the editor item. */
    public boolean isManaged(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Byte flag = meta == null ? null
            : meta.getPersistentDataContainer().get(managedKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    /** Sets the stored managed flag; returns false if it did not change. */
    public boolean setManaged(ItemStack item, boolean managed) {
        if (isManaged(item) == managed) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) (managed ? 1 : 0));
        item.setItemMeta(meta);
        return true;
    }

    /** The transfer speed stored in the editor item, in items per tick. */
    public double getSpeed(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Double speed = meta == null ? null
            : meta.getPersistentDataContainer().get(speedKey, PersistentDataType.DOUBLE);
        return speed == null ? ManagedHoppers.DEFAULT_SPEED : speed;
    }

    /** Sets the stored transfer speed, in items per tick. */
    public void setSpeed(ItemStack item, double speed) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(speedKey, PersistentDataType.DOUBLE, speed);
        item.setItemMeta(meta);
    }

    /**
     * Applies the stored properties to a real hopper: claims it and sets
     * the stored speed, or hands it back to vanilla when not managed.
     */
    public void applyTo(ItemStack item, Hopper hopper, ManagedHoppers managedHoppers) {
        if (isManaged(item)) {
            managedHoppers.claim(hopper);
            managedHoppers.setSpeed(hopper, getSpeed(item));
        } else {
            managedHoppers.release(hopper);
        }
    }

    /** Plain-text label of the editor item, shown as the panel target. */
    public String itemLabel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Component name = meta == null ? null : meta.displayName();
        // The raw template text still parses fine inside the panel template.
        return name == null ? messages.raw("hopper.editor.item-name") : PLAIN_TEXT.serialize(name);
    }
}
