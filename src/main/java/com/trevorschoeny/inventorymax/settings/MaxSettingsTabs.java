package com.trevorschoeny.inventorymax.settings;

import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventorymax.config.IMKeybinds;
import com.trevorschoeny.inventorymax.pocket.PocketHudMode;

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
import net.minecraft.client.Minecraft;
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
 * Inventory Max's tabs in the Inventory Plus settings menu: Pockets,
 * Equipment Slots and Mend Anywhere. Inventory Plus holds their places with
 * greyed stand-ins under the same ids; adding them here replaces those, in
 * the same places.
 *
 * <p>The two mods share the menu's name and the three tab ids below, and two
 * calls in Inventory Plus's {@code api}: the menu's confirm, which every
 * Reset to Defaults asks through, and the hook General's Reset everything
 * runs Inventory Max's resets by.
 *
 * <p>Half wired: a control with an {@code IMConfig} setting behind it reads
 * it every frame and saves on change; the rest (the inventory-button
 * toggles, the reach) are greyed placeholders, as in Inventory Plus's tabs.
 */
public final class MaxSettingsTabs {

    private MaxSettingsTabs() {}

    /** Inventory Plus's settings menu. */
    private static final Identifier MENU = Identifier.fromNamespaceAndPath("inventoryplus", "settings");

    /** Call once from client init, before the menu can be opened. */
    public static void register() {
        add("inventorymax:pockets", "Pockets", MaxSettingsTabs::pockets);
        add("inventorymax:equipment_slots", "Equipment Slots", MaxSettingsTabs::equipmentSlots);
        add("inventorymax:mend_anywhere", "Mend Anywhere", MaxSettingsTabs::mendAnywhere);
        // General's Reset everything reaches Inventory Max's settings too.
        InventoryPlusApi.registerSettingsReset(() -> {
            IMConfig.resetAll();
            resetKeys(IMKeybinds.CYCLE_FORWARD, IMKeybinds.CYCLE_BACKWARD);
        });
    }

    private static void add(String id, String label, Supplier<List<PanelElement>> body) {
        Tabs.addTo(MENU, Tabs.tab(id).label(Component.literal(label)).body(body));
    }

    // Tab bodies. Each has the frame Inventory Plus's tabs have: the title, a
    // description, Reset to Defaults, the on/off switch, a line, then one
    // setting per line, all greyed while the feature is off. Every reach
    // setting is in Inventory Plus's Reach tab.

    private static List<PanelElement> pockets() {
        return new Body()
                .frame("Pockets", "Adds up to three extra slots behind each hotbar slot, "
                        + "and keys to cycle through them.",
                        IMConfig::pocketCyclerEnabled, IMConfig::setPocketCyclerEnabled,
                        () -> {
                            IMConfig.reset("pocketCyclerEnabled", "pocketsShowButton", "pocketHudMode");
                            resetKeys(IMKeybinds.CYCLE_FORWARD, IMKeybinds.CYCLE_BACKWARD);
                        })
                .checkbox("Show the Pockets button",
                        IMConfig::pocketsShowButton, IMConfig::setPocketsShowButton, () -> false)
                .choice("Beside the hotbar", Arrays.asList(PocketHudMode.values()),
                        m -> m == PocketHudMode.NONE ? "Off" : "Mini hotbar",
                        IMConfig::pocketHudMode, IMConfig::setPocketHudMode, () -> false)
                .heading("Cycling")
                .key(IMKeybinds.CYCLE_FORWARD)
                .key(IMKeybinds.CYCLE_BACKWARD)
                .build();
    }

    private static List<PanelElement> equipmentSlots() {
        return new Body()
                .frame("Equipment Slots", "Adds an elytra slot and a totem slot to your inventory.",
                        IMConfig::equipmentSlotsEnabled, IMConfig::setEquipmentSlotsEnabled,
                        () -> IMConfig.reset("equipmentSlotsEnabled", "equipmentSlotsShowButton",
                                "elytraSlotEnabled", "totemSlotEnabled", "equipmentHudCue"))
                .checkbox("Show the Equipment Slots button",
                        IMConfig::equipmentSlotsShowButton, IMConfig::setEquipmentSlotsShowButton, () -> false)
                // Each slot can be turned off on its own.
                .checkbox("Elytra slot", IMConfig::elytraSlotEnabled, IMConfig::setElytraSlotEnabled, () -> false)
                .checkbox("Totem slot", IMConfig::totemSlotEnabled, IMConfig::setTotemSlotEnabled, () -> false)
                .checkbox("Show elytra and totem icons beside the hotbar",
                        IMConfig::equipmentHudCue, IMConfig::setEquipmentHudCue, () -> false)
                .build();
    }

    private static List<PanelElement> mendAnywhere() {
        return new Body()
                .frame("Mend Anywhere", "Mending items repair from XP anywhere in your inventory, "
                        + "not only in your hands and armor.",
                        IMConfig::mendInventoryItems, IMConfig::setMendInventoryItems,
                        () -> IMConfig.reset("mendInventoryItems", "mendAnywhereShowButton"))
                .checkbox("Show the Mend Anywhere button",
                        IMConfig::mendAnywhereShowButton, IMConfig::setMendAnywhereShowButton, () -> false)
                .build();
    }

    /** Puts {@code keys} back to their default binding, through Keybindery as the key buttons do. */
    private static void resetKeys(KeyMapping... keys) {
        for (KeyMapping key : keys) {
            KeybinderyAPI.getInstance().setChord(key, IChordKeyMapping.defaultChord(key));
        }
    }

    // Rows. A copy of Inventory Plus's SettingsBody, trimmed to what these
    // three tabs use; it is internal to Inventory Plus, so it can't be shared.
    // If a third mod adds tabs, these row builders belong in MenuKit instead.

    private static final class Body {
        /**
         * A divider's colour on the inventory look; the options look draws
         * vanilla's separator. Text is never hand-coloured: each label names its
         * role and the menu's kind of screen colours it (MenuKit 6.1.0), so these
         * tabs read on either look Inventory Plus's menu is made as.
         */
        private static final int LINE_COLOR = 0xFF8B8B8B;

        private final List<PanelElement> out = new ArrayList<>();
        private int y = 0;

        /** While the feature is off, every control after the frame greys and disables. */
        private BooleanSupplier featureOn = () -> true;

        /**
         * The frame, as Inventory Plus's tabs have it: the title, the
         * description, then Reset to Defaults (asking first through Inventory
         * Plus's menu, then running {@code reset}) with the On/Off switch after
         * it, at the left, then the line.
         */
        Body frame(String title, String description, BooleanSupplier on, Consumer<Boolean> setOn,
                   Runnable reset) {
            // The tab's title, bold, as on Inventory Plus's tabs. The mod's name
            // is Inventory Plus's bar across the whole menu panel, not here.
            out.add(TextLabel.builder().at(0, y).text(Component.literal(title).withStyle(ChatFormatting.BOLD))
                    .role(TextRole.HEADING).build());
            y += 14;
            out.add(TextLabel.builder().at(0, y).text(Component.literal(description)).build());
            y += 16;
            // Under the description, above the line: Reset furthest left, then
            // the switch.
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

        /** {@code unavailable}, and also while the feature is off. */
        private BooleanSupplier gated(BooleanSupplier unavailable) {
            BooleanSupplier on = featureOn;
            return () -> !on.getAsBoolean() || unavailable.getAsBoolean();
        }

        /** Settings text, turning grey while the feature is off. */
        private TextLabel settingText(int x, int y, Component text, TextRole role) {
            BooleanSupplier on = featureOn;
            return TextLabel.builder().at(x, y).text(text).role(role)
                    .disabledWhen(() -> !on.getAsBoolean()).build();
        }

        // A setting is its text on one line and its control below it, except a
        // checkbox, inline with its text; settings sit in categories. As in
        // Inventory Plus's tabs.

        /** A category: its title and a short line under it; greys while the feature is off. */
        Body heading(String text) {
            if (y > 0) y += 8;
            out.add(settingText(0, y, Component.literal(text), TextRole.HEADING));
            y += 12;
            out.add(Divider.horizontal().at(0, y - 2).size(160, 1).color(LINE_COLOR).build());
            y += 2;
            return this;
        }

        /** A setting's text, on its own line. */
        private void label(Component text) {
            out.add(settingText(0, y, text, TextRole.BODY));
            y += 12;
        }

        /** A working key: Keybindery's button, labelled with the key's name. */
        Body key(KeyMapping key) {
            label(Component.translatable(key.getName()));
            out.add(ChordButton.builder(key).at(0, y).disabledWhen(gated(() -> false)).build());
            y += 20;
            return this;
        }

        /** A setting with a few named values, bound to its config and greyed while {@code unavailable}. */
        <T> Body choice(String label, List<T> values, Function<T, String> name,
                        Supplier<T> get, Consumer<T> set, BooleanSupplier unavailable) {
            label(Component.literal(label));
            out.add(Dropdown.<T>builder()
                    .at(0, y)
                    .size(110, 16)
                    .items(values)
                    .label(v -> Component.literal(name.apply(v)))
                    .state(get, set)
                    .disabledWhen(gated(unavailable))
                    .build());
            y += 20;
            return this;
        }

        /** A checkbox bound to its setting, greyed while {@code unavailable}. */
        Body checkbox(String label, BooleanSupplier get, Consumer<Boolean> set, BooleanSupplier unavailable) {
            out.add(Checkbox.builder().at(0, y).label(Component.literal(label)).state(get, set)
                    .disabledWhen(gated(unavailable)).build());
            y += 16;
            return this;
        }

        List<PanelElement> build() {
            return List.copyOf(out);
        }
    }
}
