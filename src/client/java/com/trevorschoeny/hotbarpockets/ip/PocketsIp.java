package com.trevorschoeny.hotbarpockets.ip;

import com.trevorschoeny.hotbarpockets.HotbarPockets;
import com.trevorschoeny.hotbarpockets.PocketInput;
import com.trevorschoeny.hotbarpockets.PocketPixelPanels;
import com.trevorschoeny.hotbarpockets.PocketsSettings;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;

import net.minecraft.resources.Identifier;

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
 * </ul>
 * Locks and Reach need nothing here: IP's vetoes judge Pocket Cycler through SlotKit,
 * and Reach lists the "Pockets" set.
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
    }
}
