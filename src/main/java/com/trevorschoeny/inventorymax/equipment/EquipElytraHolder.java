package com.trevorschoeny.inventorymax.equipment;

import net.minecraft.world.item.ItemStack;

/**
 * Duck interface mixed onto {@code HumanoidRenderState} so the equipment-slot
 * elytra can travel on the render state from extraction to the wings layer. The
 * render state is the only link between the entity (which has the equipment
 * slot) and {@code WingsLayer.submit} (which only receives the render state).
 * This lets the wings layer draw the slot's elytra with vanilla's own code, in
 * every context that renders the player (third person, inventory preview and
 * so on).
 */
public interface EquipElytraHolder {

    /** The equipment-slot elytra captured for this frame, or EMPTY. */
    ItemStack inventoryMax$getEquipElytra();

    void inventoryMax$setEquipElytra(ItemStack stack);
}
