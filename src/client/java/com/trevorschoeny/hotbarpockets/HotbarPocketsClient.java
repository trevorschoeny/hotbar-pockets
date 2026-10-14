package com.trevorschoeny.hotbarpockets;

import com.trevorschoeny.hotbarpockets.ip.PocketsIp;

import com.trevlar.menukit.api.hud.HudCycleStrip;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client init: the keys, the settings and per-world counts, the hover row and its chrome, the cycle
 * strip and, only with Inventory Plus installed, its integration. The {@code ip}
 * package is touched nowhere else, so IP's classes never load without IP.
 */
public final class HotbarPocketsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PocketKeys.register();
        PocketsSettings.load();
        PocketState.load();
        // The slots are SlotKit's, declared in common init; this gives their panels the
        // per-frame screen position, then the hover tick that decides which column shows.
        PocketGeometry.install();
        PocketHover.register();
        PocketPixelPanels.register();
        PocketInput.register();
        HudCycleStrip.register(PocketStrip.INSTANCE);
        if (FabricLoader.getInstance().isModLoaded("inventoryplus")) PocketsIp.register();
    }
}
