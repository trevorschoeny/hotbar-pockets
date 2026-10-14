package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A fixed-length window onto a larger backing {@link Storage}. Gives each
 * (hotbar, depth) pocket slot its own 1-slot view of the shared 27-slot
 * {@link Pockets#POCKETS} attachment.
 *
 * <h3>Advancements</h3>
 *
 * Registered slots are backed by a separate container, so they don't feed
 * vanilla's {@code InventoryChangeTrigger} the way real inventory slots do, and
 * SlotKit deliberately has no advancement API. So this fires the trigger
 * itself: on a <b>server-side</b> write of a non-empty stack, it calls
 * {@link CriteriaTriggers#INVENTORY_CHANGED} for the owning player, which gives
 * pockets the same advancement behavior as normal inventory slots.
 */
public final class SliceStorage implements Storage {

    private final Storage backing;
    private final int offset;
    private final int len;
    private final Player player;

    public SliceStorage(Storage backing, int offset, int len, Player player) {
        this.backing = backing;
        this.offset = offset;
        this.len = len;
        this.player = player;
    }

    @Override
    public ItemStack getStack(int localIndex) {
        return backing.getStack(offset + localIndex);
    }

    @Override
    public void setStack(int localIndex, ItemStack stack) {
        backing.setStack(offset + localIndex, stack);
        // Server-side, non-empty writes trigger the same criterion real
        // inventory slots do.
        if (!stack.isEmpty()
                && !player.level().isClientSide()
                && player instanceof ServerPlayer sp) {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(sp, sp.getInventory(), stack);
        }
    }

    @Override
    public int size() {
        return len;
    }

    @Override
    public void markDirty() {
        backing.markDirty();
    }
}
