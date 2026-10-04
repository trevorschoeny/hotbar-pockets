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
 * Gliding from the equipment elytra slot — the elytra slot's content is read as
 * if it were a chest-slot glider (Trev, 2026-06-17: "as if it were a chestplate
 * slot"). Universal: {@code canGlide} runs on both sides (client predicts the
 * glide-start, server is authoritative); {@code updateFallFlying}'s durability is
 * server-gated by vanilla.
 *
 * <p>Vanilla's whole gliding lifecycle keys off {@code canGlide()} →
 * {@code canGlideUsing(getItemBySlot(slot), slot)} over the real equipment slots.
 * We widen <em>where</em> the gliding elytra is found, reusing vanilla's exact
 * checks and physics — reimplementing nothing (§0030):
 *
 * <ul>
 *   <li><b>Start + maintain.</b> {@code Player.tryToStartFallFlying} and the
 *       per-tick {@code updateFallFlying} both gate on {@code canGlide()}. We
 *       inject at its returns: if vanilla found no worn glider but the player is
 *       airborne with an equipment elytra, allow the glide. The two-elytra rule
 *       falls out for free — vanilla iterates the real CHEST slot first, so a
 *       worn gliding elytra makes {@code canGlide} true before we run, leaving the
 *       equipment elytra inert.</li>
 *   <li><b>Durability + crash guard.</b> Vanilla wears the glider down in
 *       {@code updateFallFlying} by picking a random gliding-capable
 *       <em>equipment</em> slot. When we're gliding off the equipment elytra (a
 *       chestplate, or nothing, in the real chest slot), that candidate list is
 *       empty and {@code Util.getRandom} would crash — and nothing would wear our
 *       elytra. We wrap that pick: empty list ⇒ damage our elytra exactly as
 *       vanilla would and hand back the (always-empty for players) BODY slot so
 *       vanilla's own {@code hurtAndBreak} no-ops. When our elytra reaches
 *       durability 1, {@code canGlideUsing}'s nextDamageWillBreak check fails →
 *       {@code canGlide} goes false → flight stops, matching vanilla.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class EquipmentGlideMixin {

    @Inject(method = "canGlide", at = @At("RETURN"), cancellable = true)
    private void inventoryMax$equipCanGlide(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // vanilla already allows it (worn glider — chest wins)
        if (!IMConfig.elytraSlotOn()) return; // elytra slot off → no slot-granted glide
        LivingEntity self = (LivingEntity) (Object) this;
        // Replicate vanilla's airborne guard: never glide on the ground, as a
        // passenger, or under levitation — even with an equipment elytra.
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
            return original.call(slots, random); // a worn glider exists → vanilla wears it
        }
        // Gliding off the equipment elytra (no worn glider): vanilla's list is empty.
        // Wear our elytra down (write-back so the change actually persists + syncs).
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player) {
            EquipmentSlots.wearElytra(player, self, 1);
        }
        return EquipmentSlot.BODY; // player BODY item is empty → vanilla's hurtAndBreak no-ops
    }
}
