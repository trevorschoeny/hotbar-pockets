package com.trevorschoeny.inventorymax.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.trevorschoeny.inventorymax.containerlocks.ContainerLocks;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocked automation: hoppers and droppers treat a locked container slot as
 * untouchable. They neither pull from it nor push into it.
 *
 * <p>Reads the SHARED lock with the menu-free accessor
 * ({@link ContainerLocks#isLocked(Container, int)}), since automation touches a
 * container by index with no {@code Slot} and no open menu. Server-only in
 * practice (vanilla hopper and dropper ticks run on the server). <b>No
 * capability gate</b>: a machine has no player, and machines always respect
 * locks (the player who built the machine is the one who set them).
 *
 * <p>Two per-slot transfer points cover every automation case. Both are
 * vanilla's private static helpers, so a double chest resolves to the owning
 * half automatically:
 * <ul>
 *   <li><b>{@code tryTakeInItemFromSlot}</b>: a hopper pulling from a source
 *       slot. A locked source slot reports "nothing taken" ({@code false}), so
 *       the hopper moves on to the next slot.</li>
 *   <li><b>{@code tryMoveInItem}</b>: a hopper <i>or dropper</i> inserting into
 *       a destination slot (droppers push through this same helper). A locked
 *       destination slot reports "nothing moved" (the input stack returned
 *       unchanged), so vanilla's loop skips it like a full slot.</li>
 * </ul>
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperLockEnforcementMixin {

    @Inject(method = "tryTakeInItemFromSlot", at = @At("HEAD"), cancellable = true)
    private static void inventoryMax$blockExtractFromLocked(
            Hopper hopper, Container container, int slot, Direction direction,
            CallbackInfoReturnable<Boolean> cir) {
        if (ContainerLocks.isLocked(container, slot)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tryMoveInItem", at = @At("HEAD"), cancellable = true)
    private static void inventoryMax$blockInsertIntoLocked(
            Container source, Container destination, ItemStack stack, int slot, Direction direction,
            CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerLocks.isLocked(destination, slot)) {
            cir.setReturnValue(stack);
        }
    }

    // A hopper ejecting its own locked slot. Vanilla's ejectItems reads each of
    // its own slots with getItem(i) before pushing it out; a locked slot reads as
    // empty there, so the eject loop skips it (unlocked slots still push
    // normally). The destination guard above (tryMoveInItem) protects the slot
    // the item lands in.
    @WrapOperation(
            method = "ejectItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack inventoryMax$skipLockedHopperEjectSlot(
            HopperBlockEntity hopper, int slot, Operation<ItemStack> original) {
        if (ContainerLocks.isLocked(hopper, slot)) {
            return ItemStack.EMPTY;
        }
        return original.call(hopper, slot);
    }
}
