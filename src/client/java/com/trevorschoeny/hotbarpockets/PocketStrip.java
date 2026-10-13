package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.hud.HudCycleStrip;
import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pockets' ring on MenuKit's cycle strip, the mini hotbar beside the held slot. A
 * row: the held stack, then pocket 0, 1, 2, which is the order a forward turn brings
 * them to the hand ({@link PocketServerOps#rotate}).
 *
 * <h3>Reading the contents</h3>
 * The items come straight from the player's pocket attachment on the client. In the
 * world the client's pocket slots are inert, but the server's are always live, so
 * vanilla slot sync keeps the client's attachment filled.
 *
 * <h3>Predicting the turn</h3>
 * The server turns the ring and the new order arrives a round trip later, but the strip
 * slides the moment the key is pressed. So {@link #predict} stores the turned ring and
 * {@link #viewFor} returns it for a short window, or the strip would slide the old order
 * and then jump.
 */
public final class PocketStrip implements HudCycleStrip.Source {

    public static final PocketStrip INSTANCE = new PocketStrip();

    private PocketStrip() {}

    /** How long a predicted ring is shown; covers the rotation's round trip. */
    private static final long PREDICTION_WINDOW_MILLIS = 500L;
    private static int predictedHotbar = -1;
    private static @Nullable List<ItemStack> predictedRing = null;
    private static long predictedUntil = 0L;

    @Override
    public HudCycleStrip.@Nullable View viewFor(int hotbarSlot) {
        if (!PocketsSettings.enabled() || PocketsSettings.hud() != PocketHudMode.MINI_HOTBAR) return null;
        if (predictedRing != null && predictedHotbar == hotbarSlot
                && System.currentTimeMillis() < predictedUntil) {
            return new HudCycleStrip.View(predictedRing, 0);
        }
        List<ItemStack> ring = ring(hotbarSlot);
        return ring == null ? null : new HudCycleStrip.View(ring, 0);
    }

    /**
     * Stores the ring {@code hotbar} will have after one turn, before the server confirms
     * it. Forward brings the next member to the hand, so the ring shifts one left.
     */
    public static void predict(int hotbar, boolean forward) {
        List<ItemStack> ring = ring(hotbar);
        if (ring == null) return;
        List<ItemStack> next = new ArrayList<>(ring);
        if (forward) next.add(next.removeFirst());
        else next.addFirst(next.removeLast());
        predictedHotbar = hotbar;
        predictedRing = List.copyOf(next);
        predictedUntil = System.currentTimeMillis() + PREDICTION_WINDOW_MILLIS;
    }

    /** The live ring for a hotbar slot, held stack first, or null without pockets. */
    private static @Nullable List<ItemStack> ring(int hotbar) {
        int count = PocketState.count(hotbar);
        Minecraft mc = Minecraft.getInstance();
        if (count < 1 || mc.player == null) return null;
        Storage pockets = Pockets.POCKETS.bind(mc.player);
        List<ItemStack> ring = new ArrayList<>(count + 1);
        ring.add(mc.player.getInventory().getItem(hotbar));
        for (int depth = 0; depth < count; depth++) {
            ring.add(pockets.getStack(Pockets.flatIndex(hotbar, depth)));
        }
        return ring;
    }
}
