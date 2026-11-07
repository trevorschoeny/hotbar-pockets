package com.trevorschoeny.hotbarpockets;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;

import java.util.function.IntConsumer;

/**
 * Pocket keybind input.
 *
 * <ul>
 *   <li><b>In the world</b> (no screen): the cycle keys rotate the selected
 *       hotbar slot's pockets. Polled with {@code consumeClick()} in a client
 *       tick (keybinds only fire when no screen is open).</li>
 *   <li><b>On a screen</b> (any container screen, creative's inventory tab included): the cycle
 *       keys rotate the hovered pocket column. Handled with {@code ScreenKeyboardEvents}, since key
 *       presses go to the screen, not the keybind poll, while a screen is open.</li>
 * </ul>
 *
 * <p>There's no attach key: pockets show on hover and are added or resized
 * with the on-screen plus and minus buttons.
 *
 * <p>The server does the rotation: this sends a {@link PocketRotateC2S} and
 * slides the HUD ring (PocketStrip.slide), and the server moves the items and syncs back.
 */
public final class PocketInput {

    private PocketInput() {}

    /**
     * Runs just before a rotation is sent, with the hotbar slot it turns. Inventory
     * Plus's integration sets it so Restock doesn't read the turn as the held item
     * running out ({@code ip.PocketsIp}); without IP it does nothing.
     */
    public static volatile IntConsumer beforeRotate = hotbar -> {};

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PocketInput::onClientTick);

        // In-screen handling: the cycle keys turn the shown column. A column is shown only
        // where the pockets are (PocketGeometry.pocketsPresent), so on any other screen
        // there's none and the keys do nothing.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenKeyboardEvents.afterKeyPress(screen).register((innerScreen, event) -> {
                if (!PocketsSettings.enabled()) return;
                if (PocketKeys.CYCLE_FORWARD.matches(event)) {
                    cycleKey(PocketHoverState.revealedHotbar(), true);
                } else if (PocketKeys.CYCLE_BACKWARD.matches(event)) {
                    cycleKey(PocketHoverState.revealedHotbar(), false);
                }
            });
        });
    }

    private static void onClientTick(Minecraft mc) {
        boolean inWorld = mc.gui.screen() == null && mc.player != null;
        int selected = (mc.player != null) ? mc.player.getInventory().getSelectedSlot() : -1;

        boolean fwd = false, back = false;
        while (PocketKeys.CYCLE_FORWARD.consumeClick()) fwd = true;
        while (PocketKeys.CYCLE_BACKWARD.consumeClick()) back = true;
        if (!inWorld) return;
        if (fwd) cycleKey(selected, true);
        if (back) cycleKey(selected, false);
    }

    // The Forward key (Right arrow) turns the ring backward: the hand's item goes to pocket 0
    // and the last pocket's comes into the hand. PocketRing's forward is the Backward key's.
    private static void cycleKey(int hotbar, boolean forwardKey) {
        cycle(hotbar, !forwardKey);
    }

    /** Sends a rotation request for {@code hotbar} and starts the HUD animation. Package-private for the preview shots. */
    static void cycle(int hotbar, boolean forward) {
        if (!PocketsSettings.enabled()) return;
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        int count = PocketState.count(hotbar);
        if (count < 1) return; // a cycle needs at least one pocket besides the hotbar slot
        // Asked before sending: Inventory Plus's locks live on this client, and a
        // dedicated server has no Inventory Plus to ask. The server asks again for
        // every other mod's vetoes.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!PocketRing.rotationAllowed(mc.player.inventoryMenu, mc.player, hotbar, count,
                PocketOperations.POCKET_CYCLE, PocketOperations.POCKET_CYCLE)) return;
        beforeRotate.accept(hotbar);
        ClientPlayNetworking.send(new PocketRotateC2S(hotbar, forward,
                PocketOperations.POCKET_CYCLE.id(), PocketOperations.POCKET_CYCLE.id()));
        // The HUD's prediction holds the turned ring until the server's arrives, so the
        // slide draws against it even if an older sync lands meanwhile.
        PocketStrip.predict(hotbar, forward);
        // Turn this client's copy now, so the new item is in hand from the first frame of
        // the slide: a swing or use right after reaches the server after the turn, since
        // packets keep their order. A turn the server refuses comes back as a resync.
        PocketRing.turn(mc.player, hotbar, count, forward);
        PocketStrip.slide(hotbar, forward);
    }
}
