package com.trevorschoeny.inventorymax.containerlocks;


import com.mojang.serialization.Codec;
import com.trevlar.menukit.containers.api.state.SlotState;
import com.trevlar.menukit.containers.api.state.SlotStateChannel;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.SlotOperations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.util.Set;


/**
 * Container locks, IM's half of Inventory Plus's Locked Slots: shared,
 * server-persistent locks on placed storage containers.
 *
 * <p>This class is universal and has no IP reference, so it holds the channel
 * and answers enforcement on both sides, including a dedicated server where IP
 * (client-only) is absent. Client-side UI recognition is in
 * {@link ContainerLockProvider}, registered on the client only.
 *
 * <h3>What MenuKit provides</h3>
 *
 * A single {@code SHARED} per-slot channel: one lock value per slot, synced
 * live to every viewer and writable by any of them. On shulkers it also
 * survives the box being broken and placed again. This class registers the
 * channel and writes nothing else.
 *
 * <h3>Scope: placed simple storage containers</h3>
 *
 * {@link #handles} matches by real (server-side) type: single and trapped chest
 * ({@link ChestBlockEntity}), barrel, shulker, hopper, dispenser and dropper
 * ({@link DispenserBlockEntity}), and the {@link CompoundContainer} behind a
 * double chest (MenuKit resolves each half to its own placed-chest key). It
 * excludes functional containers (furnace, brewing stand and so on), the player
 * inventory, and the ender chest (IP's client-side lock).
 */
public final class ContainerLocks {

    private ContainerLocks() {}

    /** SHARED boolean lock channel, one value per placed-container slot. */
    public static SlotStateChannel<Boolean> CHANNEL;

    // The vanilla operations a lock refuses: the menukit: part of Inventory
    // Plus's Slot lock default (Reach.SLOT_LOCK_DENIES), so a placed-container
    // lock means what a player-inventory lock means. The server keeps these
    // locks as plain booleans with no group, so it holds the default; Inventory
    // Plus's own operations (sort, restock) are judged on the locking player's
    // client by its own veto. World pickup (INVENTORY_INSERT) is left out: it
    // only fills the player's own inventory, never a placed container.
    private static final Set<BehaviorKey<?>> LOCK_DENIES = Set.of(
            BehaviorKeys.SHIFT_CLICK_IN, BehaviorKeys.SHIFT_CLICK_OUT, BehaviorKeys.COLLECT,
            BehaviorKeys.DROP, BehaviorKeys.DROP_STACK,
            BehaviorKeys.DRAG_FILL, BehaviorKeys.HOTBAR_SWAP, BehaviorKeys.OFFHAND_SWAP);

    /**
     * Registers the channel and the veto that enforces it. Universal; call once
     * at common init before any container menu opens.
     */
    public static void register() {
        CHANNEL = SlotState.register(
                Identifier.fromNamespaceAndPath("inventoryplus", "container_lock"),
                Codec.BOOL,
                StreamCodec.<RegistryFriendlyByteBuf, Boolean>of(
                        (buf, v) -> buf.writeBoolean(v),
                        buf -> buf.readBoolean()),
                false,
                SlotStateChannel.Visibility.SHARED,
                // A viewer of a placed simple-storage container may lock its
                // slots; the server has already checked the menu, the slot and
                // stillValid (since MenuKit 6.0.0). Without a rule every
                // client write to a SHARED channel reverts.
                (player, slot) -> handles(slot.container()));
        // One veto for every seam MenuKit asks it at (shift-click merge and
        // destination, collect, clicks, swaps, drops), replacing the three
        // lock mixins. On the client the open container is a SimpleContainer,
        // which handles() refuses, so this binds where the real block entity is.
        SlotOperations.veto((ref, operation) -> {
            boolean denied = LOCK_DENIES.contains(operation) && isLocked(ref.container(), ref.containerSlot());
            // [reach-probe] diagnostic: this store is judged on the server and
            // Inventory Plus judges its own on the client. A line here with no
            // matching line from Inventory Plus means the two disagree.
            if (denied) com.trevorschoeny.inventorymax.InventoryMax.LOGGER.info("[reach-probe] IM DENY thread={} op={} containerSlot={} container={}",
                    Thread.currentThread().getName(), operation.id(), ref.containerSlot(),
                    ref.container().getClass().getSimpleName());
            return denied;
        });
    }

    /**
     * True if {@code container} is a placed simple-storage container this feature
     * locks, by its real server-side type. The subclass checks cover trapped
     * chest (a {@link ChestBlockEntity}) and dropper (a {@link DispenserBlockEntity});
     * the {@link CompoundContainer} case is the double chest, which MenuKit
     * resolves per slot to the owning half. Used by server enforcement and the
     * automation paths; the client UI uses {@link ContainerLockProvider} instead.
     */
    public static boolean handles(Container container) {
        return container instanceof ChestBlockEntity
                || container instanceof BarrelBlockEntity
                || container instanceof ShulkerBoxBlockEntity
                || container instanceof HopperBlockEntity
                || container instanceof DispenserBlockEntity
                || container instanceof CompoundContainer;
    }

    /**
     * Lock check for a slot. Routes through the menu-free read
     * ({@link #isLocked(Container, int)}), which derives the server from the
     * container itself, so it answers on the server thread too.
     */
    public static boolean isLocked(Slot slot) {
        return isLocked(slot.container, slot.getContainerSlot());
    }

    /**
     * Menu-free read, for automation (hopper, dropper, dispenser), which touches
     * a container by index with no Slot and no open menu. Server side; returns
     * the SHARED value, and a double chest resolves to the owning half.
     */
    public static boolean isLocked(Container container, int slotIndex) {
        return handles(container) && CHANNEL.get(container, slotIndex);
    }
}
