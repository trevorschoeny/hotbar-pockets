package com.trevorschoeny.inventorymax;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventoryplus.api.InventoryPlusOperations;
import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventorymax.config.IMKeybinds;
import com.trevorschoeny.inventorymax.containerlocks.ContainerLockProvider;
import com.trevorschoeny.inventorymax.equipment.EquipAutoRestock;
import com.trevorschoeny.inventorymax.equipment.EquipHud;
import com.trevorschoeny.inventorymax.equipment.EquipPixelPanels;
import com.trevorschoeny.inventorymax.pocket.PocketCyclable;
import com.trevorschoeny.inventorymax.pocket.PocketCyclerHudSource;
import com.trevorschoeny.inventorymax.pocket.PocketHover;
import com.trevorschoeny.inventorymax.pocket.PocketInput;
import com.trevorschoeny.inventorymax.pocket.PocketPixelPanels;
import com.trevorschoeny.inventorymax.pocket.PocketState;
import com.trevorschoeny.inventorymax.pocket.Pockets;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.api.slot.SlotGroupId;
import com.trevorschoeny.inventorymax.settings.MaxSettingsTabs;

import net.fabricmc.api.ClientModInitializer;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint for Inventory Max. Wires up the pockets' client side:
 * config, keybinds, per-world count state, input, and the pocket source for
 * IP's shared cycle HUD.
 *
 * <p>The slot mixins (slot construction) activate via {@code mixins.json}. How
 * the slots look and behave on screen (render, hover, click and reveal, in
 * survival and creative) goes through MenuKit's panel pipeline, using the
 * pixel-positioned panels registered below. IP registers the shared HUD panel
 * itself; this only adds a source to IP's {@code CycleHudRegistry}.
 */
public class InventoryMaxClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        IMConfig.load();
        IMKeybinds.register();
        // Pockets, Equipment Slots and Mend Anywhere tabs in Inventory Plus's
        // settings menu, replacing its stand-ins.
        MaxSettingsTabs.register();
        registerFeatureSwitches();
        // A player who had "pockets supply automation" off keeps that: the
        // Pockets box comes off Restock's and Auto Tool Switch's reach, once.
        // After every client init, so Inventory Plus has read its reach first.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> upgradePocketSupply());
        PocketState.load();
        PocketInput.register();
        // Add pockets to IP's shared cycle HUD, so one HUD serves both cyclers.
        PocketCyclerHudSource.register();
        // Register pockets as a hotbar cycler so Auto Tool Switch and Restock
        // can take tools from them. This is the search and cycle counterpart
        // of PocketCyclerHudSource's rendering.
        InventoryPlusApi.registerCyclable(PocketCyclable.INSTANCE);
        // Equipment-slot HUD: elytra and totem icons left of the hotbar.
        EquipHud.register();
        // Pocket and equipment slots go through MenuKit's panel pipeline on
        // pixel-positioned panels (the pocket row centered over the hovered
        // hotbar column, equipment anchored above the offhand), which is right
        // in survival and creative from one registration. PocketHover's
        // per-frame tick drives the hover state the pocket panels' origin
        // suppliers read.
        PocketHover.register();
        PocketPixelPanels.register();
        EquipPixelPanels.register();
        // Restock the totem slot from the inventory after a death save. Uses IP's
        // restock search and follows its toggle.
        ClientTickEvents.END_CLIENT_TICK.register(EquipAutoRestock::tick);

        // Plug container locks into IP's client-side lock hook, so IP's lock
        // check, edit UI, icon, and sort and move-matching skip all recognize
        // placed containers. Client-only: IP's LockedSlots is a client-only class.
        InventoryPlusApi.registerSlotLockProvider(new ContainerLockProvider());

        InventoryMax.LOGGER.info("[inventorymax] Client init, Pocket Cycler + Container Locks active.");
    }

    // "Pockets supply automation" used to be a switch of its own; it is now
    // the Pockets box in Restock's and Auto Tool Switch's reach. A config file
    // that still has it off clears those boxes, then forgets the old setting.
    private static void upgradePocketSupply() {
        Boolean legacy = IMConfig.legacyPocketsSupplyAutomation();
        if (legacy == null) return;
        if (!legacy) {
            List<SlotGroupId> pockets = new ArrayList<>();
            for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
                for (int d = 0; d < Pockets.MAX_PER_SLOT; d++) {
                    pockets.add(CreatedSlots.groupId(Pockets.panelId(n, d), Pockets.groupId(n, d)));
                }
            }
            InventoryPlusApi.denyInReach(InventoryPlusOperations.RESTOCK_TAKE, pockets);
            InventoryPlusApi.denyInReach(InventoryPlusOperations.AUTO_TOOL_SWITCH, pockets);
        }
        IMConfig.forgetLegacy();
    }

    // The three features' on/off switches in Inventory Plus's inventory column,
    // after Inventory Plus's own, in tab order.
    private static void registerFeatureSwitches() {
        InventoryPlusApi.addFeatureSwitch("inventorymax:pockets", "Pockets",
                net.minecraft.resources.Identifier.fromNamespaceAndPath("inventorymax", "pockets_button"),
                IMConfig::pocketCyclerEnabled, IMConfig::setPocketCyclerEnabled, IMConfig::pocketsShowButton,
                "Hover over a hotbar slot to show its pockets");
        InventoryPlusApi.addFeatureSwitch("inventorymax:equipment_slots", "Equipment Slots",
                net.minecraft.resources.Identifier.fromNamespaceAndPath("inventorymax", "equipment_slots_button"),
                IMConfig::equipmentSlotsEnabled, IMConfig::setEquipmentSlotsEnabled,
                IMConfig::equipmentSlotsShowButton, null);
        InventoryPlusApi.addFeatureSwitch("inventorymax:mend_anywhere", "Mend Anywhere",
                net.minecraft.resources.Identifier.fromNamespaceAndPath("inventorymax", "mend_anywhere_button"),
                IMConfig::mendInventoryItems, IMConfig::setMendInventoryItems, IMConfig::mendAnywhereShowButton, null);
    }
}
