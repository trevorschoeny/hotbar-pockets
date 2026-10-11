package com.trevorschoeny.inventorymax.mixin;

import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventorymax.equipment.EquipmentSlots;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.level.gameevent.GameEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Totem of undying death save from the equipment totem slot. The slot is the
 * last resort, checked after the hands. Extends vanilla's own pre-death totem
 * check, {@code checkTotemDeathProtection}, reusing vanilla's revival exactly
 * and reimplementing nothing:
 *
 * <ul>
 *   <li><b>Timing.</b> {@code checkTotemDeathProtection} runs the moment a hit
 *       would be lethal, <em>before</em> {@code die()} and before any items
 *       drop. Injecting at its RETURN keeps that timing: the player is saved
 *       before death is reached, so nothing drops. (An earlier implementation
 *       got this timing wrong.)</li>
 *   <li><b>Hands first.</b> Vanilla checks both hands before this runs; if a
 *       held totem already saved the player (returns {@code true}) this does
 *       nothing. Only when vanilla found none is the equipment slot checked, so
 *       a hand totem is always used first.</li>
 *   <li><b>Vanilla revival.</b> When the slot's totem fires, this repeats
 *       vanilla's block: heal to 1, apply the totem's {@code DEATH_PROTECTION}
 *       component effects (so regeneration, absorption, fire resistance and any
 *       custom totem behave as vanilla), play the spinning-totem animation,
 *       award the use stat and advancement, then take one from the slot.</li>
 *   <li><b>Same bypass rule.</b> Vanilla refuses to save against
 *       {@code BYPASSES_INVULNERABILITY} damage (for example {@code /kill}); the
 *       same guard is here, so the slot totem can't save the player where a
 *       held one couldn't.</li>
 * </ul>
 *
 * <p>Works in survival and creative: the slot's contents are
 * server-authoritative, so a totem placed in creative saves the player exactly
 * like one placed in survival.
 */
@Mixin(LivingEntity.class)
public abstract class EquipmentTotemMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void inventoryMax$equipTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;                              // a hand totem already saved them
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // vanilla won't save here either
        if (!IMConfig.totemSlotOn()) return;                            // totem slot off: vanilla death rules
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;

        ItemStack totem = EquipmentSlots.getTotem(player);
        DeathProtection protection = totem.get(DataComponents.DEATH_PROTECTION);
        if (protection == null) return;                                 // slot empty or not a death-protecting item

        ItemStack used = totem.copy();
        EquipmentSlots.consumeTotem(player);

        // The same revival as vanilla's checkTotemDeathProtection.
        if (self instanceof ServerPlayer sp) {
            sp.awardStat(Stats.ITEM_USED.get(used.getItem()));
            CriteriaTriggers.USED_TOTEM.trigger(sp, used);
            self.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        }
        self.setHealth(1.0F);
        protection.applyEffects(used, self);                            // the component's death_effects
        self.level().broadcastEntityEvent(self, (byte) 35);             // spinning-totem animation
        cir.setReturnValue(true);
    }
}
