package com.trevorschoeny.hotbarpockets.ip;

import com.trevorschoeny.hotbarpockets.HotbarPockets;
import com.trevorschoeny.hotbarpockets.PocketInput;
import com.trevorschoeny.hotbarpockets.PocketOperations;
import com.trevorschoeny.hotbarpockets.PocketPixelPanels;
import com.trevorschoeny.hotbarpockets.Pockets;
import com.trevorschoeny.hotbarpockets.PocketsSettings;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventoryplus.api.InventoryPlusOperations;

import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything Hotbar Pockets does with Inventory Plus, loaded only when IP is:
 * <ul>
 *   <li><b>Restock and Auto Tool Switch take from pockets</b> ({@link PocketCyclable}):
 *       pockets are extra candidates, and IP brings one to the hand with a server
 *       rotation.</li>
 *   <li><b>A cycle isn't mistaken for running out:</b> Restock is told before each
 *       rotation the player starts.</li>
 *   <li><b>The column switch:</b> Pockets' On/Off in IP's inventory column, hidden until
 *       "Show the Pockets button" is on.</li>
 *   <li><b>The Pockets tab</b> in IP's settings menu ({@link PocketsSettingsTab}), opened
 *       by Ctrl-click on the column switch or on + and -.</li>
 *   <li><b>Reach protection:</b> pockets deny every operation but a hand click, shift-click
 *       out, and the moves that run the pocket turn itself.</li>
 * </ul>
 */
public final class PocketsIp {

    private PocketsIp() {}

    public static void register() {
        InventoryPlusApi.registerCyclable(PocketCyclable.INSTANCE);
        PocketInput.beforeRotate = InventoryPlusApi::suppressRestockFor;
        PocketsSettingsTab.register();
        PocketPixelPanels.openSettings = () -> {
            InventoryPlusApi.openSettings(PocketsSettingsTab.TAB_ID);
            return true;
        };
        InventoryPlusApi.addFeatureSwitch(PocketsSettingsTab.TAB_ID, "Pockets",
                Identifier.fromNamespaceAndPath(HotbarPockets.MOD_ID, "pockets_button"),
                PocketsSettings::enabled, PocketsSettings::setEnabled, PocketsSettings::showButton,
                "Hover over a hotbar slot to show its pockets");

        List<SlotGroupId> pocketGroups = new ArrayList<>();
        for (int hotbar = 0; hotbar < Pockets.HOTBAR_SLOTS; hotbar++) {
            for (int depth = 0; depth < Pockets.MAX_PER_SLOT; depth++) {
                pocketGroups.add(Pockets.groupId(hotbar, depth));
            }
        }
        // A pocket holds what the player put there on purpose. Allowed: the player's
        // own hand (click take/put, shift-click out) and the pocket turn itself, under
        // every key it can run as: PocketCyclable forwards Restock's and Auto Tool
        // Switch's own keys straight through to PocketRing, so pulling a tool or a
        // refill through the turn is judged as that operation, not as Pocket Cycler.
        List<BehaviorKey<?>> allowed = List.of(
                BehaviorKeys.CLICK_TAKE, BehaviorKeys.CLICK_PUT, BehaviorKeys.SHIFT_CLICK_OUT,
                PocketOperations.POCKET_CYCLE,
                InventoryPlusOperations.RESTOCK_TAKE, InventoryPlusOperations.RESTOCK_PUT,
                InventoryPlusOperations.AUTO_TOOL_SWITCH);
        InventoryPlusApi.protectInReach(pocketGroups, allowed);
    }
}
