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
 * Totem-of-undying death save from the equipment totem slot — the slot is
 * consulted as a last-resort totem, after the hands (Trev, 2026-06-24: "checked
 * after your hands"). Widens vanilla's own pre-death totem check,
 * {@code checkTotemDeathProtection}, reusing vanilla's exact revival and
 * reimplementing nothing (§0030):
 *
 * <ul>
 *   <li><b>Timing.</b> {@code checkTotemDeathProtection} runs the instant a hit
 *       would be lethal — <em>before</em> {@code die()} and before any items
 *       drop. Injecting at its RETURN keeps that timing: the player is saved
 *       before death is ever reached, so nothing drops. (This is the timing Trev
 *       flagged from the old implementation.)</li>
 *   <li><b>Hands first.</b> Vanilla scans both hands before we run; if a held
 *       totem already saved them (returns {@code true}) we no-op. Only when
 *       vanilla found none do we check the equipment slot — so a hand totem is
 *       always consumed first, the slot is the last resort.</li>
 *   <li><b>Vanilla revival.</b> When our slot's totem fires we mirror vanilla's
 *       block: heal to 1, apply the totem's {@code DEATH_PROTECTION} component
 *       effects (so the regen/absorption/fire-res — and any custom totem — Just
 *       Work), play the spinning-totem animation, award the use stat +
 *       advancement — then consume one from the slot.</li>
 *   <li><b>Bypass parity.</b> Vanilla refuses to save against
 *       {@code BYPASSES_INVULNERABILITY} damage (e.g. {@code /kill}); we
 *       replicate that guard so the slot totem can't cheat death where a held one
 *       couldn't.</li>
 * </ul>
 *
 * <p>Both modes for free (§0051): the slot's content is server-authoritative, so
 * a creative-placed totem saves you exactly like a survival one.
 */
@Mixin(LivingEntity.class)
public abstract class EquipmentTotemMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void inventoryMax$equipTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;                              // a hand totem already saved them
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // vanilla won't save here either
        if (!IMConfig.totemSlotOn()) return;                            // totem slot off → vanilla death rules
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;

        ItemStack totem = EquipmentSlots.getTotem(player);
        DeathProtection protection = totem.get(DataComponents.DEATH_PROTECTION);
        if (protection == null) return;                                 // slot empty / not a death-protecting item

        ItemStack used = totem.copy();
        EquipmentSlots.consumeTotem(player);                            // shrink the slot's totem, write back

        // ── mirror vanilla's checkTotemDeathProtection revival ──
        if (self instanceof ServerPlayer sp) {
            sp.awardStat(Stats.ITEM_USED.get(used.getItem()));
            CriteriaTriggers.USED_TOTEM.trigger(sp, used);
            self.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        }
        self.setHealth(1.0F);
        protection.applyEffects(used, self);                            // the component's death_effects (vanilla reuse)
        self.level().broadcastEntityEvent(self, (byte) 35);             // spinning-totem animation
        cir.setReturnValue(true);
    }
}
