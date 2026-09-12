package top.imbring.nanaHopper.hopper;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import top.imbring.nanaHopper.i18n.Messages;

/**
 * Renders the hopper management panel from language-file templates.
 *
 * <p>The panel layout is defined by the {@code hopper.panel.template} entry
 * in the language file; editor items get their own template under
 * {@code hopper.editor.panel.template}, whose buttons run the
 * "/hopper editor ..." subcommands instead. This class only picks the right
 * status / speed parts and fills in the placeholders before handing the
 * template to {@link Messages}.
 */
public final class HopperPanel {

    /** Coordinate placeholders are filled with this for editor items. */
    private static final String NO_COORDINATE = "-";

    private final Messages messages;

    public HopperPanel(Messages messages) {
        this.messages = messages;
    }

    /**
     * Builds the management panel component for the given target.
     */
    public Component render(HopperTarget target) {
        boolean managed = target.isManaged();
        double speed = target.getSpeed();

        // Editor items get their own template whose buttons run the
        // "/hopper editor ..." subcommands.
        String prefix = target instanceof HopperTarget.EditorTarget
            ? "hopper.editor.panel"
            : "hopper.panel";

        String statusKey = managed ? prefix + ".status.managed" : prefix + ".status.vanilla";
        String statusText = messages.raw(statusKey + ".status-text");
        String changeButton = messages.raw(statusKey + ".change-button");
        String speedStatus = messages.raw(speed == ManagedHoppers.DEFAULT_SPEED
            ? prefix + ".speed.vanilla" : prefix + ".speed.modified");
        String speedButtons = messages.raw(prefix + ".speed.buttons");

        Location location = target.location();
        String x = location == null ? NO_COORDINATE : String.valueOf(location.getBlockX());
        String y = location == null ? NO_COORDINATE : String.valueOf(location.getBlockY());
        String z = location == null ? NO_COORDINATE : String.valueOf(location.getBlockZ());

        return messages.component(prefix + ".template",
            "target", target.targetLabel(),
            "x", x,
            "y", y,
            "z", z,
            "status_text", statusText,
            "change_button", changeButton,
            "speed", String.valueOf(speed),
            "speed_status", speedStatus,
            "speed_buttons", speedButtons);
    }
}
