package top.imbring.nanaHopper.hopper;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Hopper;
import top.imbring.nanaHopper.i18n.Messages;

/**
 * Renders the hopper management panel from language-file templates.
 *
 * <p>The whole panel layout is defined by the {@code panel.template} entry in
 * the language file; this class only picks the right status / speed parts and
 * fills in the placeholders before handing the template to {@link Messages}.
 */
public final class HopperPanel {

    private final ManagedHoppers managedHoppers;
    private final Messages messages;

    public HopperPanel(ManagedHoppers managedHoppers, Messages messages) {
        this.managedHoppers = managedHoppers;
        this.messages = messages;
    }

    /**
     * Builds the management panel component for the given hopper.
     */
    public Component render(Hopper hopper) {
        boolean managed = managedHoppers.isManaged(hopper.getLocation());
        double speed = managedHoppers.getSpeed(hopper);

        String statusKey = managed ? "panel.status.managed" : "panel.status.vanilla";
        String statusText = messages.raw(statusKey + ".status-text");
        String changeButton = messages.raw(statusKey + ".change-button");
        String speedStatus = messages.raw(speed == ManagedHoppers.DEFAULT_SPEED
            ? "panel.speed.vanilla" : "panel.speed.modified");

        return messages.component("panel.template",
            "x", String.valueOf(hopper.getX()),
            "y", String.valueOf(hopper.getY()),
            "z", String.valueOf(hopper.getZ()),
            "status_text", statusText,
            "change_button", changeButton,
            "speed", String.valueOf(speed),
            "speed_status", speedStatus);
    }
}
