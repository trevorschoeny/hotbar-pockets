package com.trevorschoeny.inventorymax.mixin;

import com.trevorschoeny.inventorymax.containerlocks.ContainerLocks;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocked automation, dispense-source half. Stops a dispenser or dropper from
 * firing its <b>own</b> locked slot.
 *
 * <p>The hopper mixin guards transfers <i>between</i> containers, but a
 * dispenser or dropper ejecting its own contents takes a different path: both
 * pick which slot to fire with {@link DispenserBlockEntity}'s shared
 * {@code getRandomSlot} (a dropper inherits it). This adjusts that pick: if the
 * chosen slot is locked, it returns {@code -1}, which vanilla already handles
 * as "nothing to dispense" (a harmless fail click, no eject).
 *
 * <p>It only ever turns a locked pick into the existing empty-dispenser no-op,
 * so it can't misfire or break vanilla redstone. The cost is throughput: a
 * dispenser with some locked slots now and then skips a tick when it randomly
 * picks a locked one. Its locked items are never ejected and its unlocked items
 * still fire. No capability gate, since a machine has no player and machines
 * always respect locks. A double chest can't be a dispenser, so the
 * {@code CompoundContainer} case never comes up here.
 */
@Mixin(DispenserBlockEntity.class)
public abstract class DispenserLockEnforcementMixin {

    @Inject(method = "getRandomSlot", at = @At("RETURN"), cancellable = true)
    private void inventoryMax$skipLockedDispenseSlot(
            RandomSource random, CallbackInfoReturnable<Integer> cir) {
        int slot = cir.getReturnValueI();
        if (slot >= 0 && ContainerLocks.isLocked((Container) (Object) this, slot)) {
            cir.setReturnValue(-1);
        }
    }
}
