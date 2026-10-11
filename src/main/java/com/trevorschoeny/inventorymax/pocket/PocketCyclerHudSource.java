package com.trevorschoeny.inventorymax.pocket;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventorymax.pocket.PocketHudMode;
import com.trevorschoeny.inventoryplus.api.CycleHudSource;
import com.trevorschoeny.inventoryplus.api.CycleView;
import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevlar.menukit.api.slot.Storage;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The pockets' source for the shared cycle HUD. Registered into IP's
 * {@link CycleHudRegistry} so the one Mini-hotbar HUD draws the pocket cycle
 * when the player is on a hotbar slot with pockets. The same HUD serves both
 * Column Cycler and the pockets.
 *
 * <h3>Reading contents on the client</h3>
 *
 * The pocket items are read from the player's pocket attachment on the client.
 * Even in the world (where the client's pocket <i>slots</i> are inert and
 * hidden), the server's pocket slots are always active, so vanilla slot sync
 * keeps the client-side attachment filled, and reading it directly gives the
 * live contents whether or not the pockets are shown.
 *
 * <h3>Animation sync: predicting the rotation on the client</h3>
 *
 * The server does pocket rotation: {@link PocketInput} sends a request and the
 * rotated contents arrive a round trip later. The shared HUD animation,
 * however, starts the moment the key is pressed. Without prediction the
 * animation would slide the old arrangement and the items would flip
 * mid-slide, a visible jump. (Column Cycler doesn't have this problem: its
 * rotation applies locally at once, so its view is already current when the
 * animation starts.) This does the same: {@link #predictRotation} stores the
 * rotated view immediately, and {@link #cycleViewForHotbar} returns it until
 * the server's sync catches up, so the animation is always drawn against the
 * arrangement it is moving toward.
 */
public final class PocketCyclerHudSource implements CycleHudSource {

    public static final PocketCyclerHudSource INSTANCE = new PocketCyclerHudSource();

    private PocketCyclerHudSource() {}

    // Rotation prediction (client-side, display only).
    /** How long the predicted view is used; covers the rotation's round trip. */
    private static final long PREDICTION_WINDOW_MILLIS = 500L;
    private static int predictedHotbar = -1;
    private static CycleView predictedView = null;
    private static long predictedExpiryMillis = 0L;

    /** Call at client init. */
    public static void register() {
        InventoryPlusApi.registerHudSource(INSTANCE);
    }

    /**
     * Stores the predicted rotated view for {@code hotbar} so the HUD animation
     * is drawn against the arrangement it is sliding toward, not the old one.
     * Call as soon as a rotation is requested, before the server confirms. The
     * prediction applies the same ring rotation {@link PocketServerOps#rotate}
     * does on the server, so it matches what syncs back; it is used for a short
     * window, then the live view takes over.
     */
    public static void predictRotation(int hotbar, boolean forward) {
        CycleView current = computeRealView(hotbar);
        if (current == null) return;
        List<ItemStack> old = current.visualOrder();
        int m = old.size();
        List<ItemStack> next = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            int src = forward ? ((i - 1 + m) % m) : ((i + 1) % m);
            next.add(old.get(src));
        }
        predictedHotbar = hotbar;
        predictedView = CycleView.fromVisualOrder(next);
        predictedExpiryMillis = System.currentTimeMillis() + PREDICTION_WINDOW_MILLIS;
    }

    @Override
    public CycleView cycleViewForHotbar(int hotbarSlot) {
        if (!IMConfig.pocketCyclerEnabled()) return null;
        if (IMConfig.pocketHudMode() != PocketHudMode.MINI_HOTBAR) return null;

        // Use the predicted view while it's live, so the animation stays in
        // sync with the drawn arrangement until the server's rotation syncs back.
        if (predictedView != null && predictedHotbar == hotbarSlot
                && System.currentTimeMillis() < predictedExpiryMillis) {
            return predictedView;
        }
        return computeRealView(hotbarSlot);
    }

    /** The live (server-synced) pocket view for a hotbar slot, or null if none. */
    private static CycleView computeRealView(int hotbarSlot) {
        int count = PocketState.count(hotbarSlot);
        if (count < 1) return null; // a ring needs at least 1 pocket and the hotbar slot

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        Storage pockets = Pockets.POCKETS.bind(mc.player);
        // Top to bottom as drawn: pocket count-1 down to pocket 0, then the hotbar item.
        List<ItemStack> visual = new ArrayList<>(count + 1);
        for (int depth = count - 1; depth >= 0; depth--) {
            visual.add(pockets.getStack(Pockets.flatIndex(hotbarSlot, depth)));
        }
        visual.add(mc.player.getInventory().getItem(hotbarSlot));
        return CycleView.fromVisualOrder(visual);
    }

    /**
     * The pockets go at the bottom of the stacked HUD when they share a hotbar
     * slot with another cycler. The lowest stack order takes the bottom strip,
     * nearest the hotbar.
     */
    @Override
    public int hudStackOrder() {
        return 0;
    }
}
