package com.trevorschoeny.inventorymax.pocket;

import com.trevlar.menukit.api.slot.Storage;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.TriBool;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side pocket changes: the authoritative side of cycling and of emptying
 * pockets on shrink. Works directly on the player's pocket attachment and
 * inventory, then broadcasts, so the client (whose pocket slots may be inert)
 * gets the result through vanilla slot sync.
 */
public final class PocketServerOps {

    private PocketServerOps() {}

    /**
     * Rotates the ring [hotbar slot, pocket 0 to pocket count-1] by one step.
     * FORWARD brings pocket 0 into the hand and wraps the hand's item to the
     * topmost pocket; BACKWARD is the mirror.
     */
    public static void rotate(ServerPlayer sp, int hotbar, int count, boolean forward,
                              BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (sp == null || hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        count = Math.max(0, Math.min(Pockets.MAX_PER_SLOT, count));
        if (count < 1) return; // a ring needs at least 2 members (1 pocket and the hotbar slot)
        // Asked again here, for the operation the packet names. The client asked
        // before sending, but Inventory Plus's locks live on the client, so the
        // server's answer is the one that holds for every other mod's vetoes.
        if (!PocketRing.rotationAllowed(sp.inventoryMenu, sp, hotbar, count, take, put)) return;

        Storage pockets = Pockets.POCKETS.bind(sp);
        int m = count + 1;
        ItemStack[] items = new ItemStack[m];
        // Top to bottom as drawn: pocket count-1 down to pocket 0, then the hotbar item.
        for (int i = 0; i < count; i++) {
            int depth = count - 1 - i;
            items[i] = pockets.getStack(Pockets.flatIndex(hotbar, depth));
        }
        items[count] = sp.getInventory().getItem(hotbar);

        ItemStack[] out = new ItemStack[m];
        for (int i = 0; i < m; i++) {
            out[i] = forward ? items[(i - 1 + m) % m] : items[(i + 1) % m];
        }

        for (int i = 0; i < count; i++) {
            int depth = count - 1 - i;
            pockets.setStack(Pockets.flatIndex(hotbar, depth), out[i]);
        }
        sp.getInventory().setItem(hotbar, out[count]);
        pockets.markDirty();
        sp.inventoryMenu.broadcastChanges();
    }

    /**
     * Quick-moves one pocket's contents to wherever a shift-click would send
     * them: the restock path for slots the player isn't holding. Runs the
     * authoritative {@code quickMoveStack} from the pocket's server-side slot
     * (always active) and lets the existing routing move it: vanilla equips
     * armor to the matching empty armor slot, and
     * {@code InventoryMenuQuickMoveMixin} routes a totem or elytra to its
     * equipment slot. {@code broadcastChanges} syncs the result back to the
     * client (whose pocket slot may be inert).
     *
     * <p>Does nothing when the pocket slot can't be found; the move just doesn't
     * happen, the same as when there is no replacement.
     */
    public static void quickMove(ServerPlayer sp, int hotbar, int depth,
                                 BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (sp == null || hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        if (depth < 0 || depth >= Pockets.MAX_PER_SLOT) return;
        AbstractContainerMenu menu = sp.inventoryMenu;
        int idx = PocketRing.pocketMenuIndex(menu, hotbar, depth);
        if (idx < 0) return;
        // The pocket being emptied is asked here explicitly: this path never goes
        // through doClick, so no seam judges the source.
        if (!PocketRing.takeAllowed(menu, sp, hotbar, depth, take)) return;
        // The destination is judged inside the quick-move, by MenuKit's
        // shift-click-in seams in moveItemStackTo. Run under the served operation
        // so those seams judge it as that (a restock, in practice) rather than as
        // a shift-click. The tag is a thread-local set on this, the server thread,
        // and the quick-move runs synchronously on it, so the seams see it; Inventory
        // Max's own totem routing reaches moveItemStackTo through an invoker, which
        // still runs those seams.
        SlotOperations.as(take, put, () -> menu.quickMoveStack(sp, idx));
        menu.broadcastChanges();
    }


    /**
     * Empty pocket depths {@code [from, to)} of {@code hotbar} into the
     * player's inventory; drop anything that doesn't fit. Used on shrink.
     */
    public static void evict(ServerPlayer sp, int hotbar, int from, int to) {
        if (sp == null || hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        from = Math.max(0, from);
        to = Math.min(Pockets.MAX_PER_SLOT, to);

        Storage pockets = Pockets.POCKETS.bind(sp);
        for (int depth = from; depth < to; depth++) {
            int idx = Pockets.flatIndex(hotbar, depth);
            ItemStack stack = pockets.getStack(idx);
            if (stack.isEmpty()) continue;
            ItemStack moving = stack.copy();
            pockets.setStack(idx, ItemStack.EMPTY);
            // Vanilla add-to-inventory (leaves the remainder in `moving`).
            sp.getInventory().add(moving);
            if (!moving.isEmpty()) {
                sp.drop(moving, false, Prediction.SERVER_ONLY); // no room, drop the remainder
            }
        }
        pockets.markDirty();
        sp.inventoryMenu.broadcastChanges();
    }
}
