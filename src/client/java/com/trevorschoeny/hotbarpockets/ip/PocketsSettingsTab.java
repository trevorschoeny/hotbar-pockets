package com.trevorschoeny.hotbarpockets.ip;

import com.trevorschoeny.hotbarpockets.HotbarPockets;
import com.trevorschoeny.hotbarpockets.PocketHudMode;
import com.trevorschoeny.hotbarpockets.PocketKeys;
import com.trevorschoeny.hotbarpockets.PocketsSettings;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Checkbox;
import com.trevlar.menukit.api.element.Divider;
import com.trevlar.menukit.api.element.Dropdown;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Tabs;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.TextRole;
import com.trevlar.menukit.api.element.Toggle;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.keybindery.chord.ChordButton;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Pockets tab in Inventory Plus's settings menu, Inventory Max's pockets tab
 * carried over: the On/Off switch, Show the Pockets button, Beside the hotbar, and the
 * two cycle keys. It reads {@link PocketsSettings} every frame and writes through its
 * setters, which save.
 *
 * <p>Keys are bound with Keybindery's button, as on IP's own tabs. Keybindery is safe to
 * touch here: Inventory Plus requires it, and this package loads only with IP.
 */
final class PocketsSettingsTab {

    private PocketsSettingsTab() {}

    /**
     * Inventory Plus's settings menu, by its name. IP documents this name for other
     * mods' {@code Tabs.addTo}; it's an id, not a class, so naming it reaches no IP
     * internal.
     */
    private static final Identifier MENU = Identifier.fromNamespaceAndPath("inventoryplus", "settings");

    /** The tab's id: IP's column switch opens it on Ctrl-click, and so do + and -. */
    static final String TAB_ID = HotbarPockets.MOD_ID;

    /** Call once from client init, before the menu can be opened. */
    static void register() {
        Tabs.addTo(MENU, Tabs.tab(TAB_ID).label(Component.literal("Pockets")).body(PocketsSettingsTab::body));
        // General's Reset everything resets Pockets' settings and keys too.
        InventoryPlusApi.registerSettingsReset(PocketsSettingsTab::reset);
    }

    private static void reset() {
        PocketsSettings.reset();
        // Back to the default binding, through Keybindery as the key buttons do.
        for (KeyMapping key : new KeyMapping[] { PocketKeys.CYCLE_FORWARD, PocketKeys.CYCLE_BACKWARD }) {
            KeybinderyAPI.getInstance().setChord(key, IChordKeyMapping.defaultChord(key));
        }
    }

    // The body has the frame IP's tabs have: the title, a description, Reset to
    // Defaults, the On/Off switch, a line, then one setting per line, all greyed while
    // Pockets is off.

    private static List<PanelElement> body() {
        return new Body()
                .frame("Pockets", "Adds up to three slots behind each hotbar slot. "
                        + "Hover a hotbar slot in your inventory to add them.",
                        PocketsSettings::enabled, PocketsSettings::setEnabled, PocketsSettingsTab::reset)
                .checkbox("Show the Pockets button", PocketsSettings::showButton, PocketsSettings::setShowButton)
                .choice("Beside the hotbar", Arrays.asList(PocketHudMode.values()),
                        m -> m == PocketHudMode.NONE ? "Off" : "Mini hotbar",
                        PocketsSettings::hud, PocketsSettings::setHud)
                .heading("Cycling")
                .key(PocketKeys.CYCLE_FORWARD)
                .key(PocketKeys.CYCLE_BACKWARD)
                .build();
    }

    // Rows. A copy of Inventory Plus's SettingsBody, trimmed to what this tab uses;
    // SettingsBody is internal to IP, so it can't be shared. If more mods add tabs,
    // these row builders belong in MenuKit instead.
    private static final class Body {
        /**
         * A divider's colour on the inventory look; the options look draws vanilla's
         * separator. Text is never hand-coloured: each label names its role and the
         * menu's kind of screen colours it, so the tab reads on either look.
         */
        private static final int LINE_COLOR = 0xFF8B8B8B;

        private final List<PanelElement> out = new ArrayList<>();
        private int y = 0;

        /** While the feature is off, every control after the frame greys and disables. */
        private BooleanSupplier featureOn = () -> true;

        /**
         * The title, the description, then Reset to Defaults (asking first through IP's
         * menu, then running {@code reset}) with the On/Off switch after it, then the line.
         */
        Body frame(String title, String description, BooleanSupplier on, Consumer<Boolean> setOn,
                   Runnable reset) {
            out.add(TextLabel.builder().at(0, y).text(Component.literal(title).withStyle(ChatFormatting.BOLD))
                    .role(TextRole.HEADING).build());
            y += 14;
            out.add(TextLabel.builder().at(0, y).text(Component.literal(description))
                    .role(TextRole.BODY).build());
            y += 16;
            out.add(Flow.builder().at(0, y).gap(10, 4)
                    .add(Button.builder().label(Component.literal("Reset to Defaults")).size(0, 16)
                            .onClick(() -> InventoryPlusApi.confirmInSettings("Reset " + title + " to defaults?",
                                    "Every setting and key on this tab goes back to how a fresh install has it.",
                                    reset))
                            .build())
                    .add(Toggle.builder().size(40, 16).state(on, setOn)
                            .label(() -> Component.literal(on.getAsBoolean() ? "On" : "Off")).build())
                    .build());
            y += 20;
            out.add(Divider.horizontal().at(0, y).color(LINE_COLOR).build());
            y += 6;
            featureOn = on;
            return this;
        }

        /** Greyed while the feature is off. */
        private BooleanSupplier off() {
            BooleanSupplier on = featureOn;
            return () -> !on.getAsBoolean();
        }

        /** Settings text, turning grey while the feature is off. */
        private TextLabel settingText(Component text, TextRole role) {
            return TextLabel.builder().at(0, y).text(text).role(role).disabledWhen(off()).build();
        }

        /** A category: its title and a short line under it. */
        Body heading(String text) {
            if (y > 0) y += 8;
            out.add(settingText(Component.literal(text), TextRole.HEADING));
            y += 12;
            out.add(Divider.horizontal().at(0, y - 2).size(160, 1).color(LINE_COLOR).build());
            y += 2;
            return this;
        }

        /** A setting's text, on its own line. */
        private void label(Component text) {
            out.add(settingText(text, TextRole.BODY));
            y += 12;
        }

        /** A key: Keybindery's button, which binds, resets and saves on its own. */
        Body key(KeyMapping key) {
            label(Component.translatable(key.getName()));
            out.add(ChordButton.builder(key).at(0, y).disabledWhen(off()).build());
            y += 20;
            return this;
        }

        /** A setting with a few named values. */
        <T> Body choice(String label, List<T> values, Function<T, String> name, Supplier<T> get, Consumer<T> set) {
            label(Component.literal(label));
            out.add(Dropdown.<T>builder().at(0, y).size(110, 16).items(values)
                    .label(v -> Component.literal(name.apply(v)))
                    .state(get, set).disabledWhen(off()).build());
            y += 20;
            return this;
        }

        /** A checkbox, inline with its text. */
        Body checkbox(String label, BooleanSupplier get, Consumer<Boolean> set) {
            out.add(Checkbox.builder().at(0, y).label(Component.literal(label)).state(get, set)
                    .disabledWhen(off()).build());
            y += 16;
            return this;
        }

        List<PanelElement> build() {
            return List.copyOf(out);
        }
    }
}
