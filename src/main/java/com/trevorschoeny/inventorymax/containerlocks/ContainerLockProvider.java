package com.trevorschoeny.inventorymax.containerlocks;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventoryplus.api.SlotLockProvider;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;

/**
 * Connects IP's client-side lock hook ({@link SlotLockProvider}) to the shared
 * container-lock {@link ContainerLocks#CHANNEL}.
 *
 * <p>Registered into IP's {@code LockedSlots} on the client only (IP is
 * client-only), so IP's lock check, the {@code L} keybind, edit-mode click and
 * drag, the lock icon, and the Sorting and Move Matching skip all recognize
 * placed-container locks without any change to IP's rules. Writes go through
 * the channel as C2S, and the server persists them and broadcasts them live to
 * every viewer.
 *
 * <h3>Why recognition can't use the container type here</h3>
 *
 * This runs on the client, where an open chest, shulker or barrel menu wraps a
 * generic {@code SimpleContainer}, not the real BlockEntity, so the slot's
 * container can't say it is a placed chest. It is identified by the block the
 * player opened instead, via IP's always-on {@link ContainerOpenTracker}. (On
 * the server, recognition uses the real type via {@link ContainerLocks#handles}.)
 *
 * <p>This class references IP's client-only types, so it must never load on a
 * dedicated server. Only the client entrypoint instantiates it.
 */
public final class ContainerLockProvider implements SlotLockProvider {

    @Override
    public boolean handles(Slot slot) {
        // Server or singleplayer integrated server (real type). Defensive: on the
        // client render thread the container is a SimpleContainer and this is false.
        if (ContainerLocks.handles(slot.container)) return true;
        // Client render thread: identify the placed container by its block.
        return isClientPlacedContainer(slot);
    }

    @Override
    public boolean isLocked(Slot slot) {
        return ContainerLocks.CHANNEL.get(slot);
    }

    @Override
    public void setLocked(Slot slot, boolean locked) {
        ContainerLocks.CHANNEL.set(slot, locked);
    }

    // Client side: a placed simple-storage container (chest, trapped chest,
    // barrel, shulker, hopper, dispenser, dropper), identified by the opened
    // block. Only on the render thread. Excludes the player inventory (a real
    // Inventory even on the client) and the ender chest, which is left off the
    // list because ender chest locks are IP's own client-side lock.
    private static boolean isClientPlacedContainer(Slot slot) {
        if (!"Render thread".equals(Thread.currentThread().getName())) return false;
        if (slot.container instanceof Inventory) return false;
        Block b = InventoryPlusApi.openContainerBlock();
        return b instanceof ChestBlock
                || b instanceof TrappedChestBlock
                || b instanceof BarrelBlock
                || b instanceof ShulkerBoxBlock
                || b instanceof HopperBlock
                || b instanceof DispenserBlock
                || b instanceof DropperBlock;
    }
}
