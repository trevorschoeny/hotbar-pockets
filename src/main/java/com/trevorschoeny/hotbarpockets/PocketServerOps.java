package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.storage.Storage;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.TriBool;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server-side pocket changes: the authoritative side of cycling and of emptying
 * pockets on shrink. Works directly on the player's pocket attachment and
 * inventory, then broadcasts, so the client (whose pocket slots may be inert)
 * gets the result through vanilla slot sync.
 */
public final class PocketServerOps {

    private PocketServerOps() {}

    /**
     * Rotates the ring [hotbar slot, its live pockets] by one step.
     * FORWARD brings pocket 0 into the hand and wraps the hand's item to the
     * topmost pocket; BACKWARD is the mirror.
     */
    public static void rotate(ServerPlayer sp, int hotbar, boolean forward,
                              BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (sp == null || hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        int count = Pockets.count(sp, hotbar);
        // A ring needs at least 2 members (1 pocket and the hotbar slot). Asked again here,
        // for the operation the packet names: the client asked before sending, but Inventory
        // Plus's locks live on the client, so the server's answer is the one that holds for
        // every other mod's vetoes. A refused turn sends the client the real contents back,
        // since it already made the turn itself (PocketInput).
        if (count < 1 || !PocketRing.rotationAllowed(sp.inventoryMenu, sp, hotbar, count, take, put)) {
            sp.inventoryMenu.sendAllDataToRemote();
            return;
        }
        PocketRing.turn(sp, hotbar, count, forward);
        sp.inventoryMenu.broadcastChanges();
    }

    /**
     * Quick-moves one pocket's contents to wherever a shift-click would send
     * them: the restock path for slots the player isn't holding. Runs the
     * authoritative {@code quickMoveStack} from the pocket's server-side slot
     * (live within the count; past it the slot is inert and empty, so nothing
     * moves) and lets the existing routing move it: vanilla equips
     * armor to the matching empty armor slot, and SlotKit routes a totem or an
     * elytra to a slot mod's slot that asked for shift-clicks.
     * {@code broadcastChanges} syncs the result back to the client (whose pocket
     * slot may be inert).
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
        // The destination is judged inside the quick-move, by SlotKit's
        // shift-click-in seams. Run under the served operation so those seams judge
        // it as that (a restock, in practice) rather than as a shift-click. The tag
        // is a thread-local set on this, the server thread, and the quick-move runs
        // synchronously on it, so the seams see it.
        SlotOperations.as(take, put, () -> menu.quickMoveStack(sp, idx));
        menu.broadcastChanges();
    }


    /**
     * Empty pocket depths {@code [from, to)} of {@code hotbar} into the
     * player's inventory; drop anything that doesn't fit. Used on shrink
     * ({@link PocketCounts#set}), before those pockets go inert.
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
            // Not Inventory.add: for a player in creative it deletes whatever doesn't fit.
            for (ItemStack rest : InventoryMaxRecovery.giveBack(sp.getInventory().getNonEquipmentItems(), List.of(moving))) {
                sp.drop(rest, false, Prediction.SERVER_ONLY); // no room, drop the remainder
            }
        }
        sp.getInventory().setChanged();
        pockets.markDirty();
        sp.inventoryMenu.broadcastChanges();
    }
}
