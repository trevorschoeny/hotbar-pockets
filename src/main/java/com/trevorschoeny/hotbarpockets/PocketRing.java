package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.storage.Storage;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.TriBool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The inventory-menu slots a pocket move touches, and whether an operation may
 * act on them. Common: the client asks before it sends a pocket packet, and the
 * server asks again for the operation the packet names, through this one class,
 * so the two cannot disagree about which slots a move touches.
 *
 * <p>Pocket slots are real {@link CreatedSlot}s on every {@code InventoryMenu}, on
 * both sides, even while hidden, so {@code SlotOperations.allows} can be asked
 * about them in the world with no screen open. A slot that cannot be found is
 * refused: SlotKit could not be asked, and a move that was never asked about
 * is exactly the unchecked move this exists to prevent.
 */
public final class PocketRing {

    /**
     * Turns the ring [hotbar slot, its {@code count} pockets] one step, on whichever side
     * {@code player} is. FORWARD brings pocket 0 into the hand and wraps the hand's item to
     * the top pocket; BACKWARD is the mirror. No checks: the server's
     * {@link PocketServerOps#rotate} asks first, and the client turns its own copy the
     * moment the key is pressed so the new item works at once (PocketInput).
     */
    public static void turn(Player player, int hotbar, int count, boolean forward) {
        Storage pockets = Pockets.POCKETS.bind(player);
        int m = count + 1;
        ItemStack[] items = new ItemStack[m];
        // Top to bottom as drawn: pocket count-1 down to pocket 0, then the hotbar item.
        for (int i = 0; i < count; i++) {
            items[i] = pockets.getStack(Pockets.flatIndex(hotbar, count - 1 - i));
        }
        items[count] = player.getInventory().getItem(hotbar);

        ItemStack[] out = new ItemStack[m];
        for (int i = 0; i < m; i++) {
            out[i] = forward ? items[(i - 1 + m) % m] : items[(i + 1) % m];
        }

        for (int i = 0; i < count; i++) {
            pockets.setStack(Pockets.flatIndex(hotbar, count - 1 - i), out[i]);
        }
        player.getInventory().setItem(hotbar, out[count]);
        pockets.markDirty();
    }

    private PocketRing() {}

    /**
     * Whether a rotation of {@code hotbar}'s ring, over its first {@code count}
     * pockets, may run as {@code take}/{@code put}. A rotation empties and fills
     * every slot in the ring, the hotbar slot included, so each must allow both.
     */
    public static boolean rotationAllowed(AbstractContainerMenu menu, Player player, int hotbar, int count,
                                          BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (!allowsBoth(menu, player, hotbarMenuIndex(menu, player, hotbar), take, put)) return false;
        for (int depth = 0; depth < count; depth++) {
            if (!allowsBoth(menu, player, pocketMenuIndex(menu, hotbar, depth), take, put)) return false;
        }
        return true;
    }

    /**
     * Whether a quick-move may take out of the pocket at {@code (hotbar, depth)}.
     * Where the item lands is decided by the quick-move's routing, so that side is
     * judged inside the move, by SlotKit's shift-click-in seams under the move's tag.
     */
    public static boolean takeAllowed(AbstractContainerMenu menu, Player player, int hotbar, int depth,
                                      BehaviorKey<TriBool> take) {
        int idx = pocketMenuIndex(menu, hotbar, depth);
        return idx >= 0 && SlotOperations.allows(menu, menu.getSlot(idx), player, take);
    }

    /**
     * Menu index of the pocket slot for {@code (hotbar, depth)}, matched by its
     * SlotKit group id, or {@code -1} if absent.
     */
    public static int pocketMenuIndex(AbstractContainerMenu menu, int hotbar, int depth) {
        for (int k = 0; k < menu.slots.size(); k++) {
            CreatedSlot mk = CreatedSlot.of(menu.slots.get(k));
            if (mk != null && Pockets.isPocket(mk, hotbar, depth)) return k;
        }
        return -1;
    }

    /** Menu index of {@code player}'s hotbar slot {@code hotbar}, or {@code -1}. */
    public static int hotbarMenuIndex(AbstractContainerMenu menu, Player player, int hotbar) {
        for (int k = 0; k < menu.slots.size(); k++) {
            Slot s = menu.slots.get(k);
            if (s.container == player.getInventory() && s.getContainerSlot() == hotbar) return k;
        }
        return -1;
    }

    private static boolean allowsBoth(AbstractContainerMenu menu, Player player, int idx,
                                      BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (idx < 0) return false;
        Slot slot = menu.getSlot(idx);
        return SlotOperations.allows(menu, slot, player, take)
                && (put.equals(take) || SlotOperations.allows(menu, slot, player, put));
    }
}
