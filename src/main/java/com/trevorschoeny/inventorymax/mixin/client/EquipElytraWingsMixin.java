package com.trevorschoeny.inventorymax.mixin.client;

import com.trevorschoeny.inventorymax.equipment.EquipElytraHolder;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Renders the equipment-slot elytra's wings through vanilla's own wings layer.
 *
 * <p>{@code WingsLayer.submit} reads {@code chestEquipment} and renders the wings
 * if that item has a wings equipment asset. An elytra does and a chestplate
 * doesn't, so without this a chestplate plus a slotted elytra would show no
 * wings. This redirects that one field read: when the chest item isn't a glider
 * but the equipment slot holds one, the layer gets the slot's elytra instead.
 * Vanilla's own model, texture and animation code then draws it, and because
 * only the wings layer is touched (the body armor layer reads
 * {@code chestEquipment} elsewhere), the chestplate still renders on the body.
 * Both show.
 *
 * <p>With two elytra the worn one wins: if the chest item <em>is</em> a glider,
 * vanilla already renders it and the read is left untouched.
 */
@Mixin(WingsLayer.class)
public class EquipElytraWingsMixin {

    @Redirect(
            // Full descriptor: WingsLayer also carries a synthetic bridge named `submit`
            // (erased EntityRenderState param), so a bare name would match two methods.
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;chestEquipment:Lnet/minecraft/world/item/ItemStack;",
                    opcode = Opcodes.GETFIELD))
    private ItemStack inventoryMax$wingsFromEquipSlot(HumanoidRenderState state) {
        ItemStack chest = state.chestEquipment;
        ItemStack equip = ((EquipElytraHolder) state).inventoryMax$getEquipElytra();
        if (!chest.has(DataComponents.GLIDER) && equip.has(DataComponents.GLIDER)) {
            return equip; // chest isn't a glider but the slot's item is: render its wings
        }
        return chest; // vanilla behavior, including a worn elytra winning over the slot
    }
}
