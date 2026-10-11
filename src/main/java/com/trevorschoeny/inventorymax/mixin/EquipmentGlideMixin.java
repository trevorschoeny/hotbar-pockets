package com.trevorschoeny.inventorymax.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventorymax.equipment.EquipmentSlots;

import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gliding from the equipment elytra slot: the slot's elytra counts as if it
 * were worn in the chest slot. Universal: {@code canGlide} runs on both sides
 * (the client predicts the glide start, the server is authoritative), and
 * vanilla only applies {@code updateFallFlying}'s durability loss on the server.
 *
 * <p>Vanilla's whole gliding lifecycle depends on {@code canGlide()}, which
 * calls {@code canGlideUsing(getItemBySlot(slot), slot)} over the real
 * equipment slots. This widens <em>where</em> the elytra is found, reusing
 * vanilla's exact checks and physics and reimplementing nothing:
 *
 * <ul>
 *   <li><b>Start and keep gliding.</b> {@code Player.tryToStartFallFlying} and
 *       the per-tick {@code updateFallFlying} both check {@code canGlide()}.
 *       This injects at its returns: if vanilla found no worn glider but the
 *       player is airborne with an equipment elytra, the glide is allowed. With
 *       two elytra, the worn one wins: vanilla checks the real CHEST slot first,
 *       so a worn elytra makes {@code canGlide} true before this runs and the
 *       equipment elytra stays unused.</li>
 *   <li><b>Durability and crash guard.</b> Vanilla wears the glider down in
 *       {@code updateFallFlying} by picking a random glide-capable
 *       <em>equipment</em> slot. When gliding on the equipment elytra (with a
 *       chestplate, or nothing, in the real chest slot), that list is empty and
 *       {@code Util.getRandom} would crash, and nothing would wear the equipment
 *       elytra. So this wraps that pick: on an empty list it damages the
 *       equipment elytra exactly as vanilla would, and returns the BODY slot
 *       (always empty for players) so vanilla's own {@code hurtAndBreak} does
 *       nothing. When the elytra reaches durability 1, the nextDamageWillBreak
 *       check in {@code canGlideUsing} fails, {@code canGlide} turns false and
 *       flight stops, as in vanilla.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class EquipmentGlideMixin {

    @Inject(method = "canGlide", at = @At("RETURN"), cancellable = true)
    private void inventoryMax$equipCanGlide(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // vanilla already allows it (a worn glider wins)
        if (!IMConfig.elytraSlotOn()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        // Vanilla's airborne guard: never glide on the ground, as a passenger,
        // or under levitation, even with an equipment elytra.
        if (self.onGround() || self.isPassenger() || self.hasEffect(MobEffects.LEVITATION)) return;
        if (!(self instanceof Player player)) return;
        ItemStack elytra = EquipmentSlots.getElytra(player);
        // Treat the equipment elytra as a chest-slot glider: vanilla's own check, slot CHEST.
        if (LivingEntity.canGlideUsing(elytra, EquipmentSlot.CHEST)) {
            cir.setReturnValue(true);
        }
    }

    @WrapOperation(
            method = "updateFallFlying",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;getRandom(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/lang/Object;"))
    private Object inventoryMax$equipGliderDurability(List<EquipmentSlot> slots, RandomSource random,
                                                           Operation<Object> original) {
        if (!slots.isEmpty()) {
            return original.call(slots, random); // a worn glider exists, vanilla wears it
        }
        // Gliding off the equipment elytra (no worn glider): vanilla's list is empty.
        // Wear the equipment elytra down (written back, so the change persists and syncs).
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player) {
            EquipmentSlots.wearElytra(player, self, 1);
        }
        return EquipmentSlot.BODY; // a player's BODY item is empty, so vanilla's hurtAndBreak does nothing
    }
}
