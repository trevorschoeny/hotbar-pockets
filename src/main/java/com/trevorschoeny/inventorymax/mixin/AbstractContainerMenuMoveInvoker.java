package com.trevorschoeny.inventorymax.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's {@code protected} {@link AbstractContainerMenu#moveItemStackTo}
 * so the equipment-slot shift-click router ({@link InventoryMenuQuickMoveMixin})
 * can call vanilla's own merge and place logic instead of reimplementing it.
 *
 * <p>{@code moveItemStackTo} is declared on {@code AbstractContainerMenu}, not
 * on {@code InventoryMenu}, so a {@code @Shadow} from the {@code InventoryMenu}
 * mixin can't reach it, but an {@code @Invoker} on the declaring class can.
 * Applies on both sides (shift-click resolves on server and client). Access
 * only; it injects no behavior.
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuMoveInvoker {

    @Invoker("moveItemStackTo")
    boolean inventoryMax$moveItemStackTo(ItemStack stack, int startIndex,
                                              int endIndex, boolean reverseDirection);
}
